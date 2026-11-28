package com.soulrend;

import lombok.RequiredArgsConstructor;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;

/**
 * The plugin's "Connect to Soulrend" — the home-native device-code flow (site: /api/runelite/v1/device/*).
 * Start → open the home's /runelite/connect/ page in the member's browser → poll until approved → hand the
 * account token to the caller to store. Asynchronous: every step runs on the scheduler (approval polls
 * paced by delayed rescheduling, never a sleeping thread) and the callbacks arrive on that executor
 * thread — marshal to the EDT before touching Swing. {@code done} fires exactly once.
 *
 * <p><b>Security:</b> no local listener — completion is detected purely by polling the home. The
 * browser URL is pinned to the CONFIGURED home origin (the one host the member explicitly typed
 * into the plugin config) + the fixed {@code /runelite/connect/} path: same anti-phishing shape as the
 * federation broker pin, anchored to user intent instead of a hardcoded host, which is what lets
 * this work for hosted, self-hosted, and fully-standalone sites alike.</p>
 */
@RequiredArgsConstructor
@Slf4j
public final class DeviceSignIn {
	/** Fixed path of the approval page on the home site — the only page this flow will ever open. */
	static final String LINK_PATH = "/runelite/connect/";

	/** Fallback pacing/deadline when the server response omits them. */
	static final int DEFAULT_INTERVAL_S = 5;
	static final int DEFAULT_EXPIRES_S = 600;

	enum Outcome {
		SIGNED_IN, DENIED, EXPIRED, UNAVAILABLE
	}

	/** Result: outcome + the account token when {@link Outcome#SIGNED_IN}. */
	@RequiredArgsConstructor
	static final class Result {
		final Outcome outcome;
		final String token;
	}

	final BingoApiClient apiClient;
	/** Opens the sign-in page, and runs a step after a delay (ms): the two things a test replaces. */
	final Consumer<String> browserOpener;
	final BiConsumer<Runnable, Long> scheduler;
	// Polling runs on RuneLite's shared executor, which outlives the plugin, so stopping it is ours to do.
	volatile boolean cancelled;
	private volatile boolean finished;
	private volatile PendingLink pendingLink;

	@RequiredArgsConstructor
	private static final class PendingLink {
		final String url;
		final long deadline;
	}

	/** Publish only a validated, live browser URL; the private device code never leaves the poll. */
	synchronized boolean prepareLink(String url, long deadline) {
		if (cancelled || finished || !isConfiguredHomeUrl(BingoApiClient.site(), url)) return false;
		pendingLink = new PendingLink(url, deadline);
		return true;
	}

	long remainingMillis() {
		var link = pendingLink;
		return cancelled || finished || link == null ? 0 : Math.max(0, link.deadline - System.currentTimeMillis());
	}

	/** Reopen the same approval page without requesting a new device code or poll. */
	boolean reopen() {
		var link = pendingLink;
		if (cancelled || finished || link == null || System.currentTimeMillis() >= link.deadline
			|| !isConfiguredHomeUrl(BingoApiClient.site(), link.url)) return false;
		try {
			browserOpener.accept(link.url);
			return true;
		} catch (RuntimeException e) {
			// Keep the challenge alive so the member can retry or enter the displayed code manually.
			log.debug("Could not open Soulrend sign-in page", e);
			return false;
		}
	}

	/** Production binding — real system browser + delayed steps on the shared client executor. */
	DeviceSignIn(BingoApiClient apiClient, ScheduledExecutorService executor) {
		this(apiClient, LinkBrowser::browse,
			(step, delayMs) -> executor.schedule(step, delayMs, TimeUnit.MILLISECONDS));
	}

	/** Test seam — injectable browser opener + scheduler so the flow runs offline and fast. */

	/** Send progress text and the display code separately. Callbacks run on the executor. */
	void run(Consumer<String> status, Consumer<String> displayCode, Consumer<Result> done) {
		// The first step runs on the executor too — authStart is network I/O, keep it off the EDT.
		schedule(() -> start(status, displayCode, done), 0, status, done);
	}

	void start(Consumer<String> status, Consumer<String> displayCode, Consumer<Result> done) {
		var start = apiClient.authStart();
		if (cancelled || finished) return;
		if (start == null || start.device_code == null || start.device_code.isEmpty()
			|| start.user_code == null || start.user_code.isBlank()) {
			fail(status, done, "Couldn't reach " + SetupNudge.SITE_HOST + " — check your connection and try again.", Outcome.UNAVAILABLE);
			return;
		}

		var full = start.verification_url_complete;
		var url = full != null && !full.isEmpty() ? full : start.verification_url;
		// Checked against the fixed site, not the client's address — that stays empty until there is a
		// token (BingoApiClient.configure), which is exactly the state sign-in runs in.
		if (!isConfiguredHomeUrl(BingoApiClient.site(), url)) {
			// A response steering the browser anywhere but the member's own configured site is hostile.
			log.warn("Off-site sign-in URL: {}", url);
			fail(status, done, "Sign-in blocked — the site returned a suspicious login page.", Outcome.UNAVAILABLE);
			return;
		}
		var intervalMs = Math.max(1, start.interval > 0 ? start.interval : DEFAULT_INTERVAL_S) * 1000L;
		var deadline = System.currentTimeMillis()
			+ (start.expires_in > 0 ? start.expires_in : DEFAULT_EXPIRES_S) * 1000L;
		if (!prepareLink(url, deadline)) return;
		displayCode.accept(start.user_code);
		status.accept("Enter this code at " + SetupNudge.SITE_HOST + LINK_PATH);
		reopen();
		poll(status, done, start.device_code, intervalMs, deadline);
	}

	/** One scheduled approval poll; reschedules itself (server {@code slow_down} stretches the delay)
	 * until a terminal status or the deadline. */
	void poll(Consumer<String> status, Consumer<Result> done, String deviceCode, long intervalMs, long deadline) {
		if (System.currentTimeMillis() >= deadline) {
			fail(status, done, "The code expired — try again.", Outcome.EXPIRED);
			return;
		}
		schedule(() -> {
			if (System.currentTimeMillis() >= deadline) {
				fail(status, done, "The code expired — try again.", Outcome.EXPIRED);
				return;
			}
			var nextIntervalMs = intervalMs;
			var poll = apiClient.authPoll(deviceCode);
			if (cancelled || finished) return;
			if (poll != null && poll.status != null) {
				switch (poll.status) {
					case "complete":
						if (poll.token != null && !poll.token.isEmpty()) {
							finish(status, done, "Authorization approved. Completing connection…", new Result(Outcome.SIGNED_IN, poll.token));
						} else fail(status, done, "Sign-in did not return a session. Try again.", Outcome.UNAVAILABLE);
						return;
					case "denied":
						fail(status, done, "Sign-in denied on the site.", Outcome.DENIED);
						return;
					case "expired":
						fail(status, done, "The code expired — try again.", Outcome.EXPIRED);
						return;
					case "slow_down":
						nextIntervalMs = Math.max(intervalMs, Math.max(1, poll.interval) * 1000L);
						break;
					default: // pending (a null/blank poll is a transient blip — keep polling until the deadline)
						break;
				}
			}
			poll(status, done, deviceCode, nextIntervalMs, deadline);
		}, intervalMs, status, done);
	}

	/** Schedule a flow step; an escaped {@link RuntimeException} would otherwise vanish inside the
	 * executor, so surface it as the terminal failure. */
	void fail(Consumer<String> status, Consumer<Result> done, String message, Outcome outcome) {
		finish(status, done, message, new Result(outcome, null));
	}

	private synchronized boolean finishOnce() {
		if (cancelled || finished) return false;
		finished = true;
		pendingLink = null;
		return true;
	}

	private void finish(Consumer<String> status, Consumer<Result> done, String message, Result result) {
		if (!finishOnce()) return;
		status.accept(message);
		done.accept(result);
	}

	/** Stop polling: any step not yet run is skipped and the callbacks never fire. */
	synchronized void cancel() {
		cancelled = true;
		pendingLink = null;
	}

	void schedule(Runnable step, long delayMs, Consumer<String> status, Consumer<Result> done) {
		scheduler.accept(() -> {
			if (cancelled || finished) return;
			try {
				step.run();
			} catch (RuntimeException e) {
				fail(status, done, "Sign-in failed — try again.", Outcome.UNAVAILABLE);
			}
		}, delayMs);
	}

	/**
	 * True only for an HTTPS/HTTP URL on the EXACT home origin (scheme + host + port all matching
	 * {@link BingoApiClient#CANONICAL_SITE}) with the fixed {@link #LINK_PATH} path. No credentials.
	 * HTTP is tolerated only when the home itself is HTTP (local dev builds).
	 */
	static boolean isConfiguredHomeUrl(String configuredApiUrl, String url) {
		if (configuredApiUrl == null || configuredApiUrl.isEmpty() || url == null || url.isEmpty()) return false;
		try {
			var home = new URI(configuredApiUrl);
			var target = new URI(url);
			return target.getScheme() != null && target.getHost() != null && target.getUserInfo() == null
				&& target.getScheme().equalsIgnoreCase(home.getScheme())
				&& target.getHost().equalsIgnoreCase(home.getHost()) && target.getPort() == home.getPort()
				&& target.getPath() != null && target.getPath().equals(LINK_PATH);
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
