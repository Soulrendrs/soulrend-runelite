package com.soulrend;

/**
 * What the plugin says in chat about its own setup, and how often.
 *
 * <p>THE STATE THAT SAID NOTHING. Somebody who installed Soulrend and never opened the side panel used
 * to get complete silence forever: the plugin's whole surface is a panel they have no reason to
 * click, and nothing in the game ever mentioned that there was a step left. There is one setting to
 * have now — the Account Token, which Sign in fills in — so the only question is "signed in yet?".</p>
 *
 * <p>The reminder directs clan members to the panel to connect their Soulrend account.</p>
 *
 * <p>NUDGE, NOT NAG. The first-run line is capped — {@link #FIRST_RUN_NUDGES} logins, counted in
 * config so the cap survives a restart — because somebody who has decided not to connect has
 * answered the question, and a message that repeats forever is how a plugin gets uninstalled.</p>
 *
 * <p>Pure, and RuneLite-free, so the decision can be tested without a client: the plugin supplies
 * the token and the count, and gets back what to print.</p>
 */
public final class SetupNudge {
	/** How many logins a never-configured install is told where to go before it stops asking. */
	static final int FIRST_RUN_NUDGES = 3;

	/** The site host for connection instructions — one source of truth with the address the plugin uses. */
	static final String SITE_HOST = BingoApiClient.site().replaceFirst("^https?://", "");

	enum Kind {
		/** Connected, or done asking. Say nothing. */
		NONE,
		/** Not signed in — the fresh install. */
		FIRST_RUN,
	}

	/**
	 * @param firstRunShown how many times the first-run line has already been printed on this install
	 */
	static Kind decide(String token, int firstRunShown) {
		if (token != null && !token.trim().isEmpty()) return Kind.NONE;
		return firstRunShown < FIRST_RUN_NUDGES ? Kind.FIRST_RUN : Kind.NONE;
	}

	/**
	 * The chat lines for a decision, in order. Empty for {@link Kind#NONE}.
	 *
	 * <p>Keep the first-run reminder to a single connection instruction.</p>
	 */
	static String[] lines(Kind kind) {
		switch (kind) {
			case FIRST_RUN:
				return new String[]{
					"Open the Soulrend panel and click \"Connect to Soulrend\".",
				};
			default:
				return new String[0];
		}
	}

	SetupNudge() {
	}
}
