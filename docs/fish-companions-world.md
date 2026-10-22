# Fish companions: world reactions

**Status:** W0–W8 are implemented. Species review and in-game readability review passed on 2026-10-15. Tank reactions remain deferred.

## Behavior contract

Fish can react to nearby water, darkness and lights, music, and warmth. Species traits, temperament, mood, novelty, and habituation shape which features matter and how a reaction looks. Reactions are brief and cosmetic.

Swimmers and drifters can approach a feature; walkers approach only when they have a path. Perchers and anchored fish look or lean toward features without leaving the owner. Reactions end when the owner moves away. Emotes and particles are rate-limited and follow the player's particle setting.

## Technical constraints

- World features have stable identities so novelty and cooldowns are tracked per feature.
- Feature discovery uses a per-level shared index with a 512-block-read-per-client-tick cap. Unchanged classified areas require no discovery reads while standing.
- With no world features and neutral ambient input, simulation fingerprints remain bitwise identical.
- World reactions do not change gameplay outcomes.

## Deferred decision

Decide whether tanks should become companion features (the former W9 question). No tank reaction behavior is specified until that decision is made.
