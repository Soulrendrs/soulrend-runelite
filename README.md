# Soulrend — RuneLite plugin

![Soulrend official plugin icon](icon.png)

Official RuneLite companion for the [Soulrend Bingo Portal](https://soulrend.events).

> **Status: development foundation.** The plugin has not been released, tested against production, or submitted to the RuneLite Plugin Hub. Do not install a development build expecting working Soulrend authentication or proof submission.

## Soulrend account and RuneScape character

Device approval connects RuneLite to your Soulrend website account. The sidebar shows character-specific bingo boards once you enter the game with an eligible RuneScape character.

## Automatic bingo evidence

Connecting to Soulrend offers a choice to enable automatic evidence or connect without it. Evidence is disabled by default and requires explicit consent for the connected session; existing installations must review screenshot privacy once. Enabled evidence sends screenshots of matching objective activity to soulrend.events for event review. Visible chat may appear in screenshots. You can disable the feature in RuneLite settings; previously queued evidence may retry when re-enabled. Soulrend is an external service not controlled or verified by RuneLite developers.

## Bingo completion popups

RuneLite shows confirmed completions for your active team boards in the in-game banner. Use **My tile popups** and **Team completion popups** in Soulrend's Bingo settings to control them. The first board read after sign-in establishes a silent baseline. Approval and official stat credit are picked up on the next background poll.

## Architecture

The RuneLite plugin is a client for Soulrend's existing Django API. Event eligibility, board progression, proof approval, and official Wise Old Man XP/KC credit remain server-authoritative.

## Upstream and licensing

This project is being adapted from [AhmedFathy2001/anvil-plugin](https://github.com/AhmedFathy2001/anvil-plugin), pinned initially to commit `7e45a7708b8513e90e651ff685ba5329a2896aa5`. The original Java plugin is BSD-2-Clause licensed; the fork must preserve its original copyright and license. The Anvil website has a different license and is not included.

The Soulrend plugin uses its own plugin identity, configuration namespace (`soulrendbingo`), credential storage and proof queue. Anvil settings must not be imported or modified.

## Development roadmap

1. Import and isolate the Java plugin source with the original BSD license.
2. Establish CI and a no-Anvil-network regression gate.
3. Implement Soulrend device authentication and event discovery with the Django backend.
4. Add safe live board display, screenshot proofs, retries, and XP/KC telemetry without bypassing official WOM scoring.
5. Verify with actual RuneLite and apply to the Plugin Hub.

Backend integration design: [soulrend-bingos/docs/runelite-plugin-integration.md](https://github.com/Soulrendrs/soulrend-bingos/blob/main/docs/runelite-plugin-integration.md) (pending review/merge).

The bingo start reminder is a local **Soulrend Events** banner. The plugin does not relay player chat or sync collection logs. Use RuneProfile for collection-log data.

### RuneScape name changes

After WOM processes a rename, the sidebar and evidence poll can request an identity check for the observed logged-in name. Soulrend verifies its existing linked WOM player IDs and resumes the same bingo without device re-pairing. The client never grants access using a historical alias or rewrites queued capture identities. Older queued screenshots remain on disk under their original captured RSN; use the website's proof review path if necessary. The check is throttled and requires the matching server feature.
