package com.soulrend;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.runelite.client.game.ItemStack;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Loot read off an inventory diff (a tarnished rub, a bird nest): a gear swap in the same moment isn't loot. */
public class InventoryDiffLootTest
{
	private static final int HAUBERK = 24419;
	private static final int REWARD = 1303;

	@Test
	public void tarnishedItemsAreInventoryDiffLoot()
	{
		assertTrue(SoulrendPlugin.isInventoryDiffLoot("Tarnished longsword", LootRecordType.EVENT));
		assertTrue(SoulrendPlugin.isInventoryDiffLoot("Tarnished 2h sword", LootRecordType.EVENT));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Tarnished chest", LootRecordType.EVENT));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Maggot King", LootRecordType.NPC));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot(null, LootRecordType.EVENT));
	}

	@Test
	public void nestsAndPickpocketsAreInventoryDiffLoot()
	{
		assertTrue(SoulrendPlugin.isInventoryDiffLoot("Bird nest", LootRecordType.EVENT));
		assertTrue(SoulrendPlugin.isInventoryDiffLoot("Master Farmer", LootRecordType.PICKPOCKET));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Bird nest", LootRecordType.PLAYER));
	}

	@Test
	public void rewardWindowsKeepAWornCopy()
	{
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Tombs of Amascut", LootRecordType.EVENT));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Chambers of Xeric", LootRecordType.EVENT));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Barrows", LootRecordType.EVENT));
		assertFalse(SoulrendPlugin.isInventoryDiffLoot("Clue Scroll (Elite)", LootRecordType.EVENT));
	}

	@Test
	public void playerLootAndLootKeysNeverBecomeBingoDrops()
	{
		assertTrue(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.PLAYER, "Other player"));
		assertTrue(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.EVENT, "Loot Chest"));
		assertTrue(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.EVENT, "Wilderness loot key"));
		assertFalse(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.NPC, "Zulrah"));
		assertFalse(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.EVENT, "Tombs of Amascut"));
		assertFalse(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.EVENT, "Larran's big chest"));
		assertFalse(SoulrendPlugin.isExcludedPlayerLoot(LootRecordType.PICKPOCKET, "Master Farmer"));
	}

	@Test
	public void anUnequippedItemIsDropped()
	{
		List<ItemStack> kept = SoulrendPlugin.withoutWorn(
			Arrays.asList(new ItemStack(HAUBERK, 1), new ItemStack(REWARD, 1)),
			new HashSet<>(Collections.singletonList(HAUBERK)));
		assertEquals(1, kept.size());
		assertEquals(REWARD, kept.get(0).getId());
	}
}
