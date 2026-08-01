# Wild Paths

Wild Paths is a lightweight server-side NeoForge mod for Minecraft 1.21.1. Repeated player traffic can form paths, unused dirt paths can recover, and stone can slowly become mossy during rain. Vanilla Minecraft can then continue updating the resulting blocks normally.

## How it works

Wild Paths turns player movement and environmental exposure into configurable block progression:

```text
tall_grass -> short_grass -> air
                              |
                              v
grass_block -> dirt -> dirt_path -> dirt

cobblestone  --rain--> mossy_cobblestone
stone_bricks --rain--> mossy_stone_bricks
```

Every player crossing contributes to the block being used. Walking back and forth and landing after a jump count again, while standing still does not. Each stage has a protected number of crossings followed by an increasing transition probability. Strong traffic can also give nearby surface blocks a smaller amount of wear, producing irregular, natural-looking path edges. Unfinished wear on both ground and plants slowly recovers after traffic stops. Unused dirt paths later recover through the independent timed-transition system.

## Features

- Supports configurable block-to-block transitions
- Creates configurable, multi-stage paths from repeated player traffic
- Lets configured mob types create and preserve paths and trample plants
- Tramples tall grass into short grass and short grass into air in configurable stages
- Gradually removes unfinished ground and plant wear after a configurable quiet period
- Discovers matching surface blocks incrementally around players
- Resets configurable transitions when a player walks on the block
- Supports increasing transition probabilities after a protected period
- Supports rain-dependent transitions for exposed blocks
- Protects blocks from every transition when wool is placed directly underneath
- Persists tracked positions and timestamps with each world
- Processes a bounded number of entries per interval to avoid tick-time spikes
- Never force-loads unloaded chunks and limits nearby scanning work
- Works in singleplayer and multiplayer
- Requires no client-side installation when used on a dedicated server

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer in the 21.1 line
- Java 21

## Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Place the Wild Paths JAR in the world or server `mods` directory.
3. Start the game or server once to create `config/wild_paths.json5`.
4. Edit the JSON5 file if desired, then run `/wildpaths reload` or restart.

Dedicated-server players do not need to install Wild Paths on their clients. Singleplayer installations place the JAR in the normal client `mods` directory because the integrated server runs inside the game.

The optional graphical configuration screen requires [YetAnotherConfigLib (YACL) 3.8.2](https://www.curseforge.com/minecraft/mc-mods/yacl/files/7437845) on the client. Regular players joining a dedicated server still need neither Wild Paths nor YACL. Administrators who want to use `/wildpaths config` install both client-side; the server validates their permission before accepting changes.

## Configuration

Wild Paths uses one configuration file, `config/wild_paths.json5`. JSON5 supports comments and trailing commas. The default file is:

```json5
{
  configVersion: 10,

  // Limits how much work Wild Paths performs at once.
  processing: {
    checkInterval: 200,
    maxChecksPerInterval: 1024,
    onlyOverworld: false,
    nearbyScanRadius: 24,
    nearbyScanDepth: 6,
    nearbyScanColumnsPerPlayer: 128,
  },

  // These mob types create and preserve paths and trample plants like players.
  // minecraft:villager includes both adult and baby villagers.
  trafficMobs: [
    "minecraft:villager",
  ],

  // These mob types count only while a player is riding them.
  riddenTrafficMobs: [
    "minecraft:horse",
  ],

  // Unfinished traffic wear slowly disappears when nobody uses the block.
  // 24000 ticks are one Minecraft day while the dimension is running.
  wearRecovery: {
    enabled: true,
    delayTicks: 24000,
    intervalTicks: 1200,
    amountPerInterval: 1,
  },

  // Repeated player traffic can form paths in multiple configurable stages.
  pathCreation: {
    enabled: true,
    // Air is always allowed. Entries beginning with # are block tags.
    allowedAbove: [
      // "#minecraft:flowers",
      // "minecraft:short_grass",
      // "minecraft:tall_grass",
      // "minecraft:fern",
      // "minecraft:large_fern",
      // "minecraft:dead_bush",
    ],
    transitions: [
      {
        from: "minecraft:grass_block",
        to: "minecraft:dirt",
        minimumWalks: 5,
        chance: 0.20,
        chanceIncrease: 0.10,
        maxChance: 0.80,
        neighborChance: 0.50,
      },
      {
        from: "minecraft:dirt",
        to: "minecraft:dirt_path",
        minimumWalks: 8,
        chance: 0.15,
        chanceIncrease: 0.10,
        maxChance: 0.80,
        neighborChance: 0.50,
      },
    ],
  },

  // Plants are worn down by direct and neighboring player traffic.
  trampling: {
    enabled: true,
    transitions: [
      {
        from: "minecraft:tall_grass",
        to: "minecraft:short_grass",
        minimumWalks: 1,
        chance: 0.50,
        chanceIncrease: 0.25,
        maxChance: 1.0,
        neighborChance: 0.50,
      },
      {
        from: "minecraft:short_grass",
        to: "minecraft:air",
        minimumWalks: 2,
        chance: 0.35,
        chanceIncrease: 0.20,
        maxChance: 1.0,
        neighborChance: 0.50,
      },
    ],
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
      // Each horizontal neighboring path independently has this chance to reset too.
      neighborResetChance: 0.50,
      discoverNearby: true,
    },
    {
      from: "minecraft:cobblestone",
      to: "minecraft:mossy_cobblestone",
      // A small roll for every minute of rain that can reach the block.
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:cobblestone_stairs",
      to: "minecraft:mossy_cobblestone_stairs",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:cobblestone_slab",
      to: "minecraft:mossy_cobblestone_slab",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:cobblestone_wall",
      to: "minecraft:mossy_cobblestone_wall",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:stone_bricks",
      to: "minecraft:mossy_stone_bricks",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:stone_brick_stairs",
      to: "minecraft:mossy_stone_brick_stairs",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:stone_brick_slab",
      to: "minecraft:mossy_stone_brick_slab",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
    {
      from: "minecraft:stone_brick_wall",
      to: "minecraft:mossy_stone_brick_wall",
      ticks: 0,
      chanceInterval: 1200,
      chance: 0.005,
      chanceIncrease: 0.005,
      maxChance: 0.15,
      requiresRain: true,
      dryingDelay: 2400,
      dryingInterval: 1200,
      dryingChanceDecrease: 0.01,
      resetOnWalk: false,
      neighborResetChance: 0.0,
      discoverNearby: true,
    },
  ],
}
```

Each `from` block may appear only once. `ticks` is the protected time before the first roll. `chanceInterval` controls the delay between rolls. `chance` is the initial probability from greater than `0.0` through `1.0`; `chanceIncrease` is added after each failed roll, up to `maxChance`. Walking on a rule with `resetOnWalk` restarts its protected time and probability. On a real crossing, each of the eight horizontal neighboring blocks with a `resetOnWalk` rule independently resets with the crossed rule's `neighborResetChance`; terrain one block above or below is included. Set it to `0.0` to disable neighboring resets. When `requiresRain` is enabled, a roll only happens while rain can reach the block.

Transitions are data-driven: adding another `from` and `to` pair uses the same logic without a dedicated code path. When both blocks share state properties, Wild Paths preserves them during replacement. This keeps stair direction and shape, slab type, wall connections, and `waterlogged` state intact for the default moss transformations.

Rain-dependent rules can gradually dry again. After `dryingDelay` without rain reaching the block, the accumulated probability falls by `dryingChanceDecrease` every `dryingInterval`, but never below the base `chance`. Rain resumes from the remaining probability. With the moss defaults, failed rainy rolls add 0.5 percentage points per minute up to 15%; after two dry minutes, one percentage point is removed per further minute. While rain reaches both blocks, an immediately adjacent matching mossy target adds `spreadChance` to the current roll (2 percentage points by default for moss rules). Set it to `0.0` to disable moss spreading; no residual wetness is stored after rain ends.

`pathCreation.transitions` contains the separate rules driven by configured traffic. `minimumWalks` is the guaranteed number of crossings before any roll can happen. Each later crossing rolls `chance`, increases it by `chanceIncrease` after a failure, and caps it at `maxChance`. A successful transition clears all wear at that position, so the next configured stage always starts at zero. With the defaults this produces `grass_block` -> `dirt` -> `dirt_path`. Every player and every renewed crossing counts, including walking back and forth or landing after a jump. Standing still, flying, and unconfigured creatures do not add repeated wear.

`trafficMobs` adds mob entity types that always use the same crossing, path creation, plant trampling, neighboring wear, wool protection, and dirt-path preservation rules as players. The default `minecraft:villager` entry covers adult and baby villagers. `riddenTrafficMobs` applies the same behavior only while a player is sitting on the listed mob; it contains `minecraft:horse` by default. A mob counts only when it enters or lands on another block; standing still does not add wear. Mob traffic does not run the larger nearby surface-discovery scan, which remains player-driven to keep village populations inexpensive. Both lists can be edited in the YACL screen or directly in JSON5.

Path creation requires air above the affected block unless the block above matches `allowedAbove`. The whitelist accepts exact block IDs and block tags prefixed with `#`. It is empty by default; commented examples for flowers, grass, ferns, and dead bushes can be enabled individually. Other plants or modded blocks can be added without changing the mod.

`trampling.transitions` uses the same protected-walk and increasing-probability model. The defaults produce `tall_grass` -> `short_grass` -> `air`; counts reset between both stages, tall grass's upper half is removed correctly, and each of the eight neighboring plants can receive wear according to its own `neighborChance`. Once the plant is gone, later traffic can begin wearing the ground underneath. The wool block underneath that ground protects both the plant and every ground stage.

`wearRecovery` applies to unfinished path-creation and plant-trampling progress. After `delayTicks` without new traffic, `amountPerInterval` recorded walks and failed probability rolls are removed every `intervalTicks`. New traffic restarts the quiet period. Reaching zero removes the saved entry completely. A transition that already happened is not reversed: dirt remains dirt and short grass remains short grass, while vanilla Minecraft may still update suitable blocks normally. Set `enabled` to `false` to keep partial wear indefinitely.

For every real crossing, each of the eight horizontal neighboring surface blocks or plants independently receives one additional wear point with its rule's `neighborChance`. The search follows terrain one block upward or downward, never loads chunks, and still respects each neighbor's own `minimumWalks`, probability progression, and wool protection. Set `neighborChance` to `0.0` to disable spreading for a rule.

Placing any color of wool directly underneath a block protects it from both path creation and timed transitions. Existing walk counters, probabilities, and decay timers for that block are cleared. Removing the wool removes the protection, and progress starts again from zero.

`discoverNearby` lets the bounded surface scanner find blocks without requiring a player to step directly on them. Each second it checks at most `nearbyScanColumnsPerPlayer` columns within `nearbyScanRadius`, and only `nearbyScanDepth` blocks below the surface. It skips unloaded chunks. Setting the column count to `0` disables nearby discovery. With very large tracking sets, a block can change later because work is intentionally spread across multiple processing passes. Run `/wildpaths reload` or restart the server after editing the JSON5 file.

`configVersion` is managed by Wild Paths. When a newer mod release needs a newer configuration structure, the file is upgraded automatically on server start. Existing values and custom transitions are retained, and the original file is saved beside it as `wild_paths.json5.before-vN.backup`. A newer config is never automatically downgraded by an older mod release.

## Commands

Wild Paths provides these administrator commands (permission level 2):

- `/wildpaths reload` reloads `wild_paths.json5` without restarting the server.
- `/wildpaths config` opens the YACL number-field editor for an administrator with Wild Paths and YACL installed client-side.
- `/wildpaths status` shows configured timed, path-creation, and trampling transitions plus tracked-block and processing statistics.
- `/wildpaths debug <x> <y> <z>` shows timed, path-creation, or trampling progress for one loaded block.
- `/wildpaths debug true` enables a compact live action-bar display for the block the player is looking at.
- `/wildpaths debug false` disables the live display.

The coordinate argument supports absolute and relative Minecraft coordinates, for example `/wildpaths debug ~ ~-1 ~`.

The graphical editor exposes numbers as text-entry fields rather than sliders and covers the complete Wild Paths rule set: processing and feature switches, allowed-above entries, traffic-mob lists, transition block IDs, rule order, and every per-rule value. Transition lists use `namespace:block -> namespace:block`; entries can be added, renamed, removed, and reordered. New or renamed rules receive safe defaults, so save and reopen the screen once to edit their detail fields. Saving updates the same `wild_paths.json5`; on a dedicated server, the server remains authoritative, validates the complete submitted configuration, and accepts changes only from permission-level-2 administrators.

## Building

```text
gradlew.bat build
```

The finished mod JAR is written to `build/libs/`.

Every push and pull request runs the GitHub build and stores the matching JAR as a workflow artifact. Manually running the Build workflow on `main` additionally creates a tagged GitHub Release when that `mod_version` has not been published before. Existing releases are never overwritten.

## License

Wild Paths is available under the GNU General Public License v3.0.

