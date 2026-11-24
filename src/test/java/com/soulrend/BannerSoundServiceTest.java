package com.soulrend;

import java.lang.reflect.Proxy;
import org.junit.Test;

/**
 * The service is a singleton that outlives a plugin stop/start. Its executor used to stay terminated after
 * shutdown(), so the next banner threw RejectedExecutionException out of the loot handler — before the drop
 * was queued — and the tile showed "complete" in game while nothing reached the site.
 */
public class BannerSoundServiceTest
{
	private static SoulrendConfig soundOn()
	{
		return (SoulrendConfig) Proxy.newProxyInstance(SoulrendConfig.class.getClassLoader(), new Class<?>[]{SoulrendConfig.class},
			(proxy, method, args) -> method.getName().equals("bannerSound") ? Boolean.TRUE : null);
	}

	@Test
	public void playAfterShutdownDoesNotThrow()
	{
		BannerSoundService sound = new BannerSoundService(soundOn(), null);
		sound.play(BannerSoundService.Kind.BANNER);
		sound.shutdown();
		sound.play(BannerSoundService.Kind.BANNER);
		sound.play(BannerSoundService.Kind.MISSION);
		sound.shutdown();
	}

	/**
	 * Startup lists every clip folder before any sound has played. The re-read went to the audio executor
	 * directly, which only exists after the first play — the NullPointerException escaped setRoot() and
	 * skipped the outbox, push stash and disk cache set-up that follows it, every session.
	 */
	@Test
	public void listingBeforeAnySoundDoesNotThrow()
	{
		BannerSoundService sound = new BannerSoundService(soundOn(), null);
		for (BannerSoundService.Kind kind : BannerSoundService.Kind.values())
		{
			sound.listClips(kind);
		}
		sound.shutdown();
	}
	@Test
	public void defaultVolumeIsFortyPercent() {
		org.junit.Assert.assertEquals(40, new SoulrendConfig() {}.bannerSoundVolume());
	}

	@Test
	public void bundledCuesHaveDistinctResourcesAndEnabledDefaults() {
		SoulrendConfig settings = new SoulrendConfig() {};
		java.util.Set<String> resources = new java.util.HashSet<>();
		for (BannerSoundService.BingoCue cue : BannerSoundService.BingoCue.values()) {
			org.junit.Assert.assertTrue(cue.enabled(settings));
			org.junit.Assert.assertTrue(cue.resource.startsWith("sounds/soulrend-"));
			org.junit.Assert.assertNotNull("Missing bundled bingo sound: " + cue.resource,
				BannerSoundService.class.getResource(cue.resource));
			org.junit.Assert.assertTrue(resources.add(cue.resource));
		}
	}

}
