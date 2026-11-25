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
	public void defaultVolumeIsTwentyFivePercent() {
		org.junit.Assert.assertEquals(25, new SoulrendConfig() {}.bannerSoundVolume());
	}

	@Test
	public void victorySoundHasIndependentGainTrim() {
		for (var cue : BannerSoundService.BingoCue.values()) {
			float expected = cue == BannerSoundService.BingoCue.TEAM_BINGO_COMPLETE ? -20f : -12f;
			org.junit.Assert.assertEquals(expected, BannerSoundService.cueGainDb(cue, -12f), 0.001f);
		}
		org.junit.Assert.assertEquals(-80f, BannerSoundService.cueGainDb(
			BannerSoundService.BingoCue.TEAM_BINGO_COMPLETE, -80f), 0.001f);
	}

	@Test
	public void teamVictoryHasItsOwnSwitchAndLongerAudioSpacing() {
		var cue = BannerSoundService.BingoCue.TEAM_BINGO_COMPLETE;
		org.junit.Assert.assertFalse(cue.enabled(new SoulrendConfig() {
			@Override public boolean teamBingoCompleteSound() { return false; }
		}));
		org.junit.Assert.assertTrue(cue.enabled(new SoulrendConfig() {
			@Override public boolean bingoCompletionSound() { return false; }
		}));
		org.junit.Assert.assertEquals(4300L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(BannerSoundService.cueSpacingNanos(cue)));
	}

	@Test
	public void bundledCuesHaveDistinctResourcesAndEnabledDefaults() throws java.io.IOException {
		SoulrendConfig settings = new SoulrendConfig() {};
		java.util.Set<String> resources = new java.util.HashSet<>();
		for (BannerSoundService.BingoCue cue : BannerSoundService.BingoCue.values()) {
			org.junit.Assert.assertTrue(cue.enabled(settings));
			org.junit.Assert.assertTrue(cue.resource.startsWith("sounds/soulrend-"));
			try (java.io.InputStream stream = BannerSoundService.class.getResourceAsStream(cue.resource)) {
				org.junit.Assert.assertNotNull("Missing bundled bingo sound: " + cue.resource, stream);
				byte[] wavHeader = new byte[12];
				org.junit.Assert.assertEquals("Truncated WAV: " + cue.resource, 12, stream.read(wavHeader));
				org.junit.Assert.assertEquals("RIFF", new String(wavHeader, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
				org.junit.Assert.assertEquals("WAVE", new String(wavHeader, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
			}
			org.junit.Assert.assertTrue(resources.add(cue.resource));
		}
	}

}
