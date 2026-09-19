# Donut Client

A legit-first utility client for **Minecraft 1.21.1** (Fabric). ClickGUI,
pathfinding, schematic building and auto-mining — all working through vanilla
client behavior: real camera rotations, vanilla interaction paths, input-level
movement. No anti-cheat evasion, no packet spoofing, no xray.

![status](https://img.shields.io/badge/build-passing-brightgreen) ![tests](https://img.shields.io/badge/tests-41%2F41-brightgreen) ![mc](https://img.shields.io/badge/minecraft-1.21.1-blueviolet)

## Features

### ClickGUI
- Draggable per-category panels; left-click toggles, right-click opens settings
- Sliders, dropdowns, keybind capture (with modifier combos), color swatches, text fields
- Live fuzzy search (`TAB`), settings filtered with their module
- Open with **Right Shift** (configurable), saves config on close

### Pathfinder
- Hierarchical A*: chunk-level corridor search → block-level A* inside it
- Handles step-ups, falls (bounded), water, ladders/vines, 2–3 block sprint-jump gaps
- Danger-aware routing (lava/cactus/fire weighted), optional cover preference
- Input-level walking with stuck detection and automatic replanning
- Pure-Java core, fully unit-tested headless

### SchematicBuilder
- Parses **`.litematic`** (Litematica v4–6) and **`.schem`** (Sponge v2/v3) with a
  self-contained streaming NBT reader (no Minecraft types — works on 10M+ block files)
- Placement ordering: layer / spiral / nearest, plus an attachable-safe support pass
  (torches, rails, doors... placed right after their host block)
- Places through vanilla right-click interactions; walks to out-of-range targets
- Persistent build sessions: progress is saved every 5s and resumes after a disconnect

### AutoMine
- Mines a configurable block list, rate-limited in blocks-per-minute
- Only mines blocks the **real camera raycast actually hits** — no through-walls
- Fastest-tool hotbar selection, trash auto-drop when the inventory fills

### Also included
- **InventoryManager** — auto-drops filler blocks during long sessions
- **HudOverlay** — corner-anchored FPS / coordinates / module list
- **Profiles** — named presets plus `.donutprofile` JSON export/import
- **Config** — human-readable TOML with hot-reload (edit `config.toml` while playing)
  and automatic version migration (v1 → v2)

## Quick start

```bash
# any JDK 17–25 on PATH/JAVA_HOME; the JDK 21 toolchain is auto-provisioned
./gradlew runClient          # launch a dev client with the mod

./gradlew test               # headless test suite (parsers, pathfinding, math)

./gradlew build              # → build/libs/donut-client-1.0.0.jar
```

Drop the built jar into your `mods/` folder alongside **Fabric Loader ≥ 0.15**
and **Fabric API**.

## In-game usage

| Key | Action |
|---|---|
| Right Shift | Open/close the ClickGUI |
| Left-click module | Toggle on/off |
| Right-click module | Expand settings |
| TAB (in GUI) | Search modules and settings |

- **Pathfinder**: enable, then look at a block — it walks there. Set `Goal: Coordinates`
  and enter X/Z for long trips.
- **SchematicBuilder**: put a `.litematic` in `run/schematics/`, stand where the build's
  origin should be, hit *Start Here*. Progress persists in `run/schematics/.progress/`.
- **AutoMine**: set the block list (e.g. `diamond_ore, deepslate_diamond_ore`) and enable.

## Project layout

```
src/main/java/com/donut/
├── DonutClient.java        # Fabric entrypoint
├── module/                 # Module base, registry, settings, the 5 modules
├── rotation/               # Rotation queue + easing (real camera, no packets)
├── pathfinding/            # Pure A* core + Minecraft world adapter
├── schematic/              # NBT reader, parsers, planner, placement, sessions
├── gui/clickgui/           # ClickGUI framework + widgets
├── gui/hud/                # HUD renderer
├── config/                 # TOML config, profiles, hot-reload, migration
└── event/                  # Small synchronous event bus
```

## Scope statement

This client is built for **single-player and servers where client utility mods are
permitted**. It deliberately excludes: anti-cheat evasion, "humanization" for
detection avoidance, silent/packet-level rotations, and xray. Every action it takes
is one a human at the keyboard could perform through the standard client.

## License

[MIT](LICENSE) — see also [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).
