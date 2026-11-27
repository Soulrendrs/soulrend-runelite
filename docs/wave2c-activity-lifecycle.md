# Activity rules: lifecycle invalidation

World loading, hop, disconnect, logout and full evidence reset invalidate the
activity-only snapshot generation and reset consecutive baselines. The next
full rule refresh must begin after that reset. A slow pre-reset refresh or
fast retry cannot restore stale activity authorization, even if the same
character and bearer session remain selected. The screenshot callback also
checks the generation before encoding and saving activity proof.

This is a conservative loss-of-credit boundary, not proof that a game message
represents a fresh completion. The first observation after a fresh snapshot
is still baseline-only. No changes to other evidence types or the hard-off
activity upload gate. Live client verification remains mandatory.
