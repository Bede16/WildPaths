# Wild Paths

Wild Paths is a lightweight server-side NeoForge mod for Minecraft 1.21.1. Dirt paths that players have used turn back into dirt after a configurable period without further use. Vanilla Minecraft can then grow grass on the dirt when its normal conditions are met.

## Features

- Tracks only dirt paths that players actually walk on
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

NeoForge creates `config/wild_paths-common.toml` after the first launch.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `decayTicks` | `72000` | Inactivity before decay; 72,000 ticks are three Minecraft days |
| `checkInterval` | `200` | Ticks between processing passes |
| `maxChecksPerInterval` | `1024` | Maximum paths checked per pass and dimension |
| `onlyOverworld` | `false` | Restrict tracking and decay to the Overworld |

The configured decay time is a minimum. With very large tracking sets, a path can be converted later because work is intentionally spread across multiple processing passes.

## Building

```text
gradlew.bat build
```

The finished mod JAR is written to `build/libs/`.

## License

Wild Paths is available under the GNU General Public License v3.0.
