# Quest book redesign

**Status:** implemented and reviewed in game on 20 October 2026. See the
[interaction contract](design-spec.md) and [runtime review](review/README.md). Current code is
authoritative if it differs from the contract.

## Current design

The Captain's Log uses labelled destinations, a stable token balance, and layouts suited to each
task. Quest lists show the action, qualifying condition, progress, reward previews, and inline Claim.
One row can expand for details. Market explains affordability and rerolls; Cleanup shows the active
cycle and contribution information. The interface retains the mod's bitmap font, panel textures,
and tier frames.

Long text grows a row and remains scrollable. Collection rows show caught fish and silhouettes for
uncaught species, and link to the encyclopedia. Claim and purchase actions wait for authoritative
server state before reporting success.

The old mockup gallery, one-off sprite extraction fixtures, and redundant scene captures were
removed after implementation review. The interaction contract and compact validation summary remain.
