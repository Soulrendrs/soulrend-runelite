package com.soulrend;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The first request must carry both halves of identity or roster-first linking falls back to review. */
public class IdentityStampTest
{
	@Test
	public void waitsForBothRsnAndAccountHash()
	{
		assertFalse(SoulrendPlugin.identityReady(null, 42L));
		assertFalse(SoulrendPlugin.identityReady("", 42L));
		assertFalse(SoulrendPlugin.identityReady("Hells Taco", -1L));
		// The client's placeholder before the real name arrives.
		assertFalse(SoulrendPlugin.identityReady("#Player1281", 42L));
		assertTrue(SoulrendPlugin.identityReady("Hells Taco", 42L));
	}

	@Test
	public void spotsAnInGameRenameButNotAMissingName()
	{
		assertTrue(SoulrendPlugin.renamedMidSession("1BM", "LEARNING TOB"));
		assertFalse(SoulrendPlugin.renamedMidSession("Hells Taco", "hells_taco"));
		assertFalse(SoulrendPlugin.renamedMidSession(null, "Hells Taco"));
		assertFalse(SoulrendPlugin.renamedMidSession("Hells Taco", null));
	}
}
