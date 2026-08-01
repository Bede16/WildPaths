# Wild Paths

Wild Paths is a lightweight server-side NeoForge mod for Minecraft 1.21.1. Configurable blocks that players walk on can change after a period without further use. Vanilla Minecraft can then continue updating the resulting blocks normally.

## Features

- Supports configurable block-to-block transitions
- Tracks only matching blocks that players actually walk on
- Supports rain-dependent transitions for exposed blocks
- Persists tracked positions and timestamps with each world
- Processes a bounded number of entries per interval to avoid tick-time spikes
- Never scans chunks and never force-loads unloaded chunks
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
  // Limits how much work Wild Paths performs at once.
  processing: {
    checkInterval: 200,
    maxChecksPerInterval: 1024,
    onlyOverworld: false,
  },

  // A block is observed only after a player walks on it.
  "transitions": [
    {
      from: "minecraft:dirt_path",
      to: "minecraft:dirt",
      ticks: 72000,
    },
    {
      from: "minecraft:cobblestone",
      to: "minecraft:mossy_cobblestone",
      ticks: 240000,
      requiresRain: true,
    },
    {
      from: "minecraft:stone_bricks",
      to: "minecraft:mossy_stone_bricks",
      ticks: 240000,
      requiresRain: true,
    },
  ],
}
```

Each `from` block may appear only once. `ticks` is the minimum inactivity time. When `requiresRain` is enabled, the transition waits until rain can reach the block. With very large tracking sets, a block can change later because work is intentionally spread across multiple processing passes. Restart the server after editing the JSON5 file.

## Building

```text
gradlew.bat build
```

The finished mod JAR is written to `build/libs/`.

## License

Wild Paths is available under the GNU General Public License v3.0.
