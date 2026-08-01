# Wild Paths

Wild Paths is a lightweight server-side NeoForge mod for Minecraft 1.21.1. Dirt paths can recover after a period without use, while stone can slowly become mossy during rain. Vanilla Minecraft can then continue updating the resulting blocks normally.

## Features

- Supports configurable block-to-block transitions
- Discovers matching surface blocks incrementally around players
- Resets configurable transitions when a player walks on the block
- Supports increasing transition probabilities after a protected period
- Supports rain-dependent transitions for exposed blocks
- Persists tracked positions and timestamps with each world
- Processes a bounded number of entries per interval to avoid tick-time spikes
- Never force-loads unloaded chunks and limits nearby scanning work
- Works in singleplayer and multiplayer
- Requires no client-side installation when used on a dedicated server

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer in the 21.1 line
- Java 21

## Configuration

Wild Paths creates one configuration file, `config/wild_paths.json5`, on the first server start. JSON5 supports comments and trailing commas. The default file is:

```json5
{
  configVersion: 2,

  // Limits how much work Wild Paths performs at once.
  processing: {
    checkInterval: 200,
    maxChecksPerInterval: 1024,
    onlyOverworld: false,
    nearbyScanRadius: 24,
    nearbyScanDepth: 6,
    nearbyScanColumnsPerPlayer: 128,
  },

  // Matching surface blocks are discovered gradually near active players.
  "transitions": [
    {
      from: "minecraft:dirt_path",
      to: "minecraft:dirt",
      // One protected hour after the last use, then a roll every five minutes.
      ticks: 72000,
      chanceInterval: 6000,
      chance: 0.05,
      chanceIncrease: 0.05,
      maxChance: 1.0,
      resetOnWalk: true,
      discoverNearby: true,
    },
    {
      from: "minecraft:cobblestone",
      to: "minecraft:mossy_cobblestone",
      // A small roll for every minute of rain that can reach the block.
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      requiresRain: true,
      resetOnWalk: false,
      discoverNearby: true,
    },
    {
      from: "minecraft:stone_bricks",
      to: "minecraft:mossy_stone_bricks",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      requiresRain: true,
      resetOnWalk: false,
      discoverNearby: true,
    },
  ],
}
```

Each `from` block may appear only once. `ticks` is the protected time before the first roll. `chanceInterval` controls the delay between rolls. `chance` is the initial probability from greater than `0.0` through `1.0`; `chanceIncrease` is added after each failed roll, up to `maxChance`. Walking on a rule with `resetOnWalk` restarts its protected time and probability. When `requiresRain` is enabled, a roll only happens while rain can reach the block.

`discoverNearby` lets the bounded surface scanner find blocks without requiring a player to step directly on them. Each second it checks at most `nearbyScanColumnsPerPlayer` columns within `nearbyScanRadius`, and only `nearbyScanDepth` blocks below the surface. It skips unloaded chunks. Setting the column count to `0` disables nearby discovery. With very large tracking sets, a block can change later because work is intentionally spread across multiple processing passes. Run `/wildpaths reload` or restart the server after editing the JSON5 file.

`configVersion` is managed by Wild Paths. When a newer mod release needs a newer configuration structure, the file is upgraded automatically on server start. Existing values and custom transitions are retained, and the original file is saved beside it as `wild_paths.json5.before-vN.backup`. A newer config is never automatically downgraded by an older mod release.

## Commands

Wild Paths provides these administrator commands (permission level 2):

- `/wildpaths reload` reloads `wild_paths.json5` without restarting the server.
- `/wildpaths status` shows the number of configured transitions, tracked blocks, enabled dimensions, and current processing limits.
- `/wildpaths debug <x> <y> <z>` shows the transition, remaining protected time, next roll, failed rolls, current chance, and rain state for one loaded block.

The coordinate argument supports absolute and relative Minecraft coordinates, for example `/wildpaths debug ~ ~-1 ~`.

## Building

```text
gradlew.bat build
```

The finished mod JAR is written to `build/libs/`.

Successful builds on `main` automatically create a tagged GitHub Release when the `mod_version` has not been published before. The matching mod JAR is attached directly to that release. Further commits with the same version leave the existing release unchanged.

## License

Wild Paths is available under the GNU General Public License v3.0.

