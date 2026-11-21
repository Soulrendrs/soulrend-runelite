package com.soulrend;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The lists that moved out of SoulrendPlugin into soulrend_text.json load back intact — same entries, same
 * order, emoji and quotes included — through the same Gson path startUp uses.
 */
public class ShippedTextTest
{
	@Test
	public void loadsEveryList() throws Exception
	{
		SoulrendPlugin.ShippedText text;
		try (Reader r = new InputStreamReader(SoulrendPlugin.class.getResourceAsStream("/soulrend_text.json"), StandardCharsets.UTF_8))
		{
			text = new Gson().fromJson(r, SoulrendPlugin.ShippedText.class);
		}
		assertEquals(24, text.alwaysNotify.size());
		assertEquals("infernal cape", text.alwaysNotify.get(0));
		assertEquals("fire cape", text.alwaysNotify.get(23));
		assertEquals("GRANDMASTER", text.questTiers.get("desert treasure ii"));
		assertEquals("MASTER", text.questTiers.get("recipe for disaster - culinaromancer"));
		assertEquals("MASTER", text.questTiers.get("legends' quest"));
		assertEquals("MASTER", text.questTiers.get("desert treasure"));
		assertEquals("NOVICE", text.questTiers.get("cook's assistant"));
		assertEquals("EXPERIENCED", text.questTiers.get("rag and bone man ii"));
		assertTrue(text.questTiers.size() > 200);
	}
}
