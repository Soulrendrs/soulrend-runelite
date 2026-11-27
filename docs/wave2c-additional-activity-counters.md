# Wave 2C: additional candidate activity counters

Stable IDs 8–10 append to the shared v1 occurrence contract. Existing IDs
are unchanged. All three are distinct from the website's current WOM metric
catalogue, not a new XP/KC/item-gain mechanism.

- 8: Guardians of the Rift — closed-rift counter, not points, loot or a
  generic game-state change.
- 9: Colossal Wyrm advanced agility lap — only the exact course/mode.
- 10: Bird's egg offering — an offering count, not a nest gained.

Source review (2026-11-27):
- https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java
  (GUARDIANS_OF_THE_RIFT_PATTERN, BIRD_EGG_OFFERING_PATTERN and KILLCOUNT_PATTERN).
- https://github.com/runelite/runelite/issues/20449 (advanced Wyrm lap message).
- https://legal.jagex.com/docs/rules/macro-and-client-features-not-permitted
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

These are candidate parsers, not observed completion evidence. Absolute counters
can also be reported historically; consecutive +1 is necessary but not sufficient
to establish a live completion. Keep both hard gates false until observed
in-game message type/timing, historical query exclusion, counter reset behavior
and actual screenshots are verified. First readings, gaps and decreases are
not credited. No raw messages, player chat or game interaction are uploaded.

Existing gathering item gains and XP stay unchanged. No blanket inference from
production XP/inventory changes, Tithe Farm spendable balances, or guessed
Foundry messages. Use existing manual quantity proof for those activities until
a specific verified detector is available. No claim of Plugin Hub approval.
