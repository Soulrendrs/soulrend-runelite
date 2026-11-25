package com.soulrend;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the plugin says about its own setup.
 *
 * A fresh install gets one connection instruction, with a cap that survives restarts.
 */
public class SetupNudgeTest
{
	@Test
	public void aFreshInstallIsToldWhereToGo()
	{
		assertEquals(SetupNudge.Kind.FIRST_RUN, SetupNudge.decide("", 0));
		assertEquals(SetupNudge.Kind.FIRST_RUN, SetupNudge.decide(null, 0));
		assertEquals("blank is empty, whitespace included", SetupNudge.Kind.FIRST_RUN,
			SetupNudge.decide("\t", 0));
	}

	@Test
	public void theFreshInstallLineStopsAfterTheCap()
	{
		assertEquals(SetupNudge.Kind.FIRST_RUN, SetupNudge.decide("", SetupNudge.FIRST_RUN_NUDGES - 1));
		assertEquals(SetupNudge.Kind.NONE, SetupNudge.decide("", SetupNudge.FIRST_RUN_NUDGES));
		assertEquals("a count from a mangled config never re-arms it", SetupNudge.Kind.NONE,
			SetupNudge.decide("", 99));
	}

	@Test
	public void aConnectedInstallIsLeftAlone()
	{
		assertEquals(SetupNudge.Kind.NONE, SetupNudge.decide("tok", 0));
		assertArrayEquals(new String[0], SetupNudge.lines(SetupNudge.Kind.NONE));
	}

	@Test
	public void theFreshInstallShowsOnlyTheConnectionInstruction()
	{
		assertArrayEquals(new String[]{"Open the Soulrend panel and click \"Connect to Soulrend\"."},
			SetupNudge.lines(SetupNudge.Kind.FIRST_RUN));
	}

	@Test
	public void theSiteHostIsTheAddressThePluginUses()
	{
		// One source of truth: the connection instructions and the plugin's only server cannot drift apart.
		assertEquals("soulrend.events", SetupNudge.SITE_HOST);
		assertTrue(BingoApiClient.CANONICAL_SITE.endsWith(SetupNudge.SITE_HOST));
	}
}
