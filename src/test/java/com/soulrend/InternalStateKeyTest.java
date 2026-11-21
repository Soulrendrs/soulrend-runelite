package com.soulrend;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InternalStateKeyTest
{
	@Test
	public void bookkeepingWritesDoNotTriggerARefresh()
	{
		assertTrue(SoulrendPlugin.isInternalStateKey("recapCounterMinutes"));
		assertTrue(SoulrendPlugin.isInternalStateKey("recapCounterBiggestHit"));
		assertTrue(SoulrendPlugin.isInternalStateKey("vestigeRolls:zezima"));
		assertTrue(SoulrendPlugin.isInternalStateKey("clogSyncState:zezima"));
	}

	@Test
	public void memberSettingsStillDo()
	{
		assertFalse(SoulrendPlugin.isInternalStateKey("playerToken"));
		assertFalse(SoulrendPlugin.isInternalStateKey("activeClan"));
		assertFalse(SoulrendPlugin.isInternalStateKey("syncClanCoffer"));
		assertFalse(SoulrendPlugin.isInternalStateKey(null));
	}
}
