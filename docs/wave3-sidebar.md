# Wave 3 sidebar contract

The existing authenticated team board may optionally supply personal_amount
(a nonnegative long). Missing means unknown, never zero. It is an eligible
website-player projection, not a current-client session counter. No teammate
IDs or additional observations are requested. Team progress or solo best
remains authoritative; the personal row is informational, including independent
item-pool progress. Older servers omit the field.

Unchanged poll snapshots already skip redraw. Changed snapshots of the same
selected event preserve the wrapped sidebar's scroll position, clamped when
cards disappear. Explicit selection, account changes and failed reads do not
request restoration.

No detector, consent, outbound request, scoring or screenshot changes.
Pending: full CI and actual sidebar visual verification; popup and editor
integration with Waves 2B/2C.


The optional collaboration object is website-authoritative and exposes only
minimum_players, minimum_each and qualifying_players, never teammate IDs.
The sidebar and detail dialog show the distinct-player requirement separately
from the team quantity bar; even a full quantity bar cannot award completion.
Older servers omit the field, and unsupported or hidden/locked rows suppress it.
No new game events or evidence are collected.
