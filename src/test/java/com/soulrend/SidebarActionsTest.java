package com.soulrend;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.assertFalse;

public class SidebarActionsTest
{
	private static SoulrendSidebarDataSource source()
	{
		return new SoulrendSidebarDataSource(() -> new PluginConfigResponse(),
			new BingoApiClient(new Gson(), new OkHttpClient()));
	}

	@Test
	public void bannerSoundCallsAreSafeWithoutAPlugin()
	{
		SoulrendSidebarDataSource ds = source();
		ds.toggleBannerSound("nope.wav");
		ds.copyBannerSoundsPath();
		ds.importSounds(BannerSoundService.Kind.BANNER);
		assertFalse(ds.bannerSoundOn("nope.wav"));
	}
}
