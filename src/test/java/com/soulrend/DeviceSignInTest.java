package com.soulrend;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.soulrend.DeviceSignIn.isConfiguredHomeUrl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The sign-in browser pin: only the CONFIGURED home origin + /runelite/connect/ may ever open (§8 shape,
 * anchored to the member-typed Site URL instead of a hardcoded host). */
public class DeviceSignInTest
{
	private static final String HOME = "https://clan.soulrend.events";

	@Test
	public void acceptsOwnLinkDevicePage()
	{
		assertTrue(isConfiguredHomeUrl(HOME, HOME + "/runelite/connect/"));
		assertTrue(isConfiguredHomeUrl(HOME, HOME + "/runelite/connect/?code=ABCD-EFGH"));
	}

	@Test
	public void refusesForeignHosts()
	{
		assertFalse(isConfiguredHomeUrl(HOME, "https://evil.com/runelite/connect/"));
		assertFalse(isConfiguredHomeUrl(HOME, "https://clan.soulrend.events.evil.com/runelite/connect/"));
	}

	@Test
	public void refusesLookalikeAuthorityTricks()
	{
		assertFalse(isConfiguredHomeUrl(HOME, "https://clan.soulrend.events@evil.com/runelite/connect/"));
		assertFalse(isConfiguredHomeUrl(HOME, "https://clan.soulrend.events:8443/runelite/connect/")); // port mismatch
	}

	@Test
	public void refusesOtherPathsOnOwnHost()
	{
		assertFalse(isConfiguredHomeUrl(HOME, HOME + "/"));
		assertFalse(isConfiguredHomeUrl(HOME, HOME + "/profile"));
	}

	@Test
	public void refusesSchemeDowngrade()
	{
		// A https home never opens a http page; a deliberate http dev home may open its own http page.
		assertFalse(isConfiguredHomeUrl(HOME, "http://clan.soulrend.events/runelite/connect/"));
		assertTrue(isConfiguredHomeUrl("http://localhost:3000", "http://localhost:3000/runelite/connect/"));
	}

	@Test
	public void refusesGarbage()
	{
		assertFalse(isConfiguredHomeUrl(HOME, null));
		assertFalse(isConfiguredHomeUrl(HOME, ""));
		assertFalse(isConfiguredHomeUrl(HOME, "javascript:alert(1)"));
		assertFalse(isConfiguredHomeUrl(null, HOME + "/runelite/connect/"));
	}

	@Test
	public void cancelledFlowRunsNoFurtherSteps()
	{
		// The flow polls on RuneLite's shared executor, which outlives the plugin; a cancel at
		// shutdown must leave every queued step a no-op (no request, no callback).
		List<Runnable> queued = new ArrayList<>();
		List<Object> callbacks = new ArrayList<>();
		DeviceSignIn flow = new DeviceSignIn(null, url -> { }, (step, delayMs) -> queued.add(step));
		flow.run(callbacks::add, callbacks::add, callbacks::add);
		flow.cancel();
		for (Runnable step : new ArrayList<>(queued))
		{
			step.run();
		}
		assertEquals(1, queued.size());
		assertTrue(callbacks.isEmpty());
	}

	/** No network or real browser: advance the exact scheduled steps explicitly. */
	private static final class Fixture {
		final List<Runnable> queued = new ArrayList<>();
		final List<String> opened = new ArrayList<>();
		final List<String> codes = new ArrayList<>();
		final List<DeviceSignIn.Result> results = new ArrayList<>();
		final String url = BingoApiClient.site() + "/runelite/connect/?code=ABCD-EFGH";
		int starts, polls;
		boolean browserFails;
		String pollStatus = "pending";
		Runnable duringPoll = () -> {};
		final BingoApiClient api = new BingoApiClient(new com.google.gson.Gson(), new okhttp3.OkHttpClient()) {
			@Override public DeviceAuthStart authStart() {
				starts++;
				var response = new DeviceAuthStart();
				response.device_code = "private-device-secret";
				response.user_code = "ABCD-EFGH";
				response.verification_url_complete = url;
				response.expires_in = 600;
				response.interval = 5;
				return response;
			}

			@Override public DeviceAuthPoll authPoll(String code) {
				assertEquals("private-device-secret", code);
				polls++;
				duringPoll.run();
				var response = new DeviceAuthPoll();
				response.status = pollStatus;
				response.token = "session";
				return response;
			}
		};
		final DeviceSignIn flow = new DeviceSignIn(api, url -> {
			if (browserFails) throw new IllegalStateException("browser unavailable");
			opened.add(url);
		}, (step, delay) -> queued.add(step));

		void start() {
			flow.run(line -> {}, codes::add, results::add);
			step();
		}

		void step() {
			queued.remove(0).run();
		}
	}

	@Test
	public void reopensSameChallengeWithoutAnotherStartOrPoll() {
		var f = new Fixture();
		assertFalse(f.flow.reopen());
		f.start();
		assertEquals(List.of("ABCD-EFGH"), f.codes);
		assertEquals(List.of(f.url), f.opened);
		assertTrue(f.flow.remainingMillis() > 0);
		assertTrue(f.flow.reopen());
		assertTrue(f.flow.reopen());
		assertEquals(List.of(f.url, f.url, f.url), f.opened);
		assertEquals(1, f.starts);
		assertEquals(0, f.polls);
		assertEquals(1, f.queued.size());
		f.pollStatus = "complete";
		f.step();
		assertEquals(1, f.results.size());
		assertEquals(DeviceSignIn.Outcome.SIGNED_IN, f.results.get(0).outcome);
		assertEquals("session", f.results.get(0).token);
		assertFalse(f.flow.reopen());
		assertEquals(0L, f.flow.remainingMillis());
	}

	@Test
	public void browserFailureDoesNotDiscardChallenge() {
		var f = new Fixture();
		f.browserFails = true;
		f.start();
		assertEquals(List.of("ABCD-EFGH"), f.codes);
		assertTrue(f.results.isEmpty());
		assertEquals(1, f.queued.size());
		assertFalse(f.flow.reopen());
		f.browserFails = false;
		assertTrue(f.flow.reopen());
		assertEquals(List.of(f.url), f.opened);
		assertEquals(1, f.starts);
	}

	@Test
	public void cancelDuringPollDoesNotDeliverLateToken() {
		var f = new Fixture();
		f.start();
		f.pollStatus = "complete";
		f.duringPoll = f.flow::cancel;
		f.step();
		assertTrue(f.results.isEmpty());
		assertTrue(f.queued.isEmpty());
		assertFalse(f.flow.reopen());
		assertEquals(0L, f.flow.remainingMillis());
	}

	@Test
	public void expiredOrForeignLinkCannotBeReopened() {
		var f = new Fixture();
		assertFalse(f.flow.prepareLink("https://evil.com/runelite/connect/", Long.MAX_VALUE));
		assertTrue(f.flow.prepareLink(f.url, System.currentTimeMillis() - 1));
		assertFalse(f.flow.reopen());
		assertEquals(0L, f.flow.remainingMillis());
		assertTrue(f.opened.isEmpty());
		f.flow.cancel();
		assertFalse(f.flow.prepareLink(f.url, Long.MAX_VALUE));
	}

	@Test
	public void serverExpiryAndDenialClearTheLink() {
		for (String status : List.of("expired", "denied")) {
			var f = new Fixture();
			f.start();
			f.pollStatus = status;
			f.step();
			assertEquals(1, f.results.size());
			assertEquals(DeviceSignIn.Outcome.valueOf(status.toUpperCase(java.util.Locale.ROOT)), f.results.get(0).outcome);
			assertFalse(f.flow.reopen());
			assertEquals(0L, f.flow.remainingMillis());
			assertTrue(f.queued.isEmpty());
		}
	}
}
