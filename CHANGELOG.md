# Changelog

## 2.1.0

- Let nearby tall and short grass gain configurable trampling wear
- Clear protected neighboring path and plant progress before applying nearby wear
- Add a YACL screen containing number-entry fields for existing numeric settings
- Let permission-level-2 administrators edit and apply server numbers through `/wildpaths config`
- Add a configurable `trafficMobs` entity-type list with adult and baby villagers enabled by default
- Apply path creation, trampling, neighboring wear, path preservation, and wool protection to configured mobs
- Add an editable YACL category for traffic-mob entity lists
- Add `riddenTrafficMobs` with horses enabled only while a player is riding them

## 2.0.2

- Reduce the default walk requirements and raise traffic transition probabilities
- Raise the default neighboring ground-wear chance from 15% to 50%
- Increase moss probability during sustained rain and let it fall again while blocks dry
- Shorten the live debug overlay so it fits on one action-bar line
- Use only `/wildpaths debug true` and `/wildpaths debug false` for the overlay toggle
- Migrate unchanged 2.0.1 traffic defaults while preserving custom values and recovery settings

## 2.0.1

- Let unfinished path-creation wear recover after a configurable quiet period
- Apply the same gradual recovery to tall- and short-grass trampling progress
- Clear both recorded walks and accumulated failed probability rolls during recovery

## 2.0.0

- Create configurable paths from repeated player traffic
- Add the default multi-stage `grass_block` to `dirt` to `dirt_path` progression
- Add configurable `tall_grass` to `short_grass` to `air` trampling stages
- Let nearby surface blocks gradually gain configurable wear for more natural paths
- Add a configurable block and tag whitelist for plants allowed above forming paths
- Persist walk counts and increasing creation probabilities per block
- Protect blocks and reset all progress with wool placed directly underneath
- Add an optional live action-bar debug display for the block a player is looking at
- Extend `/wildpaths status` and `/wildpaths debug` with path creation details

## 1.3.0

- Add `/wildpaths reload` for configuration changes without a server restart
- Add `/wildpaths status` for tracking and processing statistics
- Add `/wildpaths debug <x> <y> <z>` for detailed per-block transition diagnostics

## 1.2.1

- Reliably reset dirt-path decay when walking on the slightly lower path block
- Add versioned automatic JSON5 configuration migrations with backups
- Automatically publish a GitHub Release and mod JAR after a successful version build

## 1.2.0

- Add a protected period and increasing decay probability for dirt paths
- Let exposed cobblestone and stone bricks slowly become mossy during rain
- Discover matching surface blocks incrementally near active players
- Persist probability attempts across server restarts
- Add bounded nearby scanning controls to the JSON5 configuration

## 1.1.0

- Replace the fixed dirt-path rule with configurable block transitions
- Use one commented JSON5 file for processing and transition settings
- Add optional rain requirements for exposed blocks

## 1.0.0

- Initial NeoForge 1.21.1 release
- Track dirt paths used by players
- Persist tracking data per dimension
- Convert inactive dirt paths back into dirt
- Add configurable decay timing and bounded processing


