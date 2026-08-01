# Changelog

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

