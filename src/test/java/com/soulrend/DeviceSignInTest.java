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
		flow.run(callbacks::add, callbacks::add);
		flow.cancel();
		for (Runnable step : new ArrayList<>(queued))
		{
			step.run();
		}
		assertEquals(1, queued.size());
		assertTrue(callbacks.isEmpty());
	}
}
