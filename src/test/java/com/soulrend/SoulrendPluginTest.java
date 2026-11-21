package com.soulrend;

import com.soulrend.scenario.SoulrendScenarioPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SoulrendPluginTest
{
	/**
	 * Dev client only. {@code -Dsoulrend.site=https://beta.soulrend.events} (or env SOULREND_SITE) points it
	 * at beta to try a site that isn't released yet. Sign in again after switching: a token is per site.
	 */
	public static void main(String[] args) throws Exception
	{
		String dev = System.getProperty("soulrend.site", System.getenv("SOULREND_SITE"));
		if (dev != null && dev.matches("https://[a-z0-9.-]+(:\\d+)?"))
		{
			BingoApiClient.useDevSite(dev);
		}
		// The scenario lab rides along in the dev client only (::soulrend help) — see resources/scenarios/README.md.
		ExternalPluginManager.loadBuiltin(SoulrendPlugin.class, SoulrendScenarioPlugin.class);
		RuneLite.main(args);
	}
}
