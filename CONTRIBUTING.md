# Contributing to Donut Client

Thanks for your interest in contributing! This is a legit-first utility client:
everything must work through vanilla client behavior. Please keep that spirit
in every contribution.

## Ground rules

- **No detection-evasion features.** Contributions that fake client behavior at
  the packet level (silent rotations, spoofed positions), tune timing around
  anti-cheat, or otherwise aim to deceive server operators will not be accepted.
- **Client-side only.** No server-side code, no mixed environments.
- **Vanilla interaction paths.** Modules act through the same code a human
  input would trigger: real camera rotations, `interactionManager` calls,
  inventory clicks the UI would send.

## Getting set up

1. Install any JDK 17–25 (Gradle 9.7.1 drives the build; the JDK 21 toolchain
   is auto-provisioned via the foojay resolver).
2. `./gradlew genSources` to decompile Minecraft for reference.
3. `./gradlew runClient` to launch a dev client with the mod loaded.

## Before you open a PR

- `./gradlew build` must be green (compiles + all tests pass).
- Add or extend tests for pure logic (parsers, pathfinding, math, config) —
  these live in `src/test/java` and must not require a Minecraft instance.
- Keep hot paths allocation-free; searches and parsing may allocate, per-tick
  code should not.
- Match the existing code style: final classes, records where sensible,
  Javadoc on public API.

## Code layout

| Package | Purpose |
|---|---|
| `com.donut.module` | Module base, registry, settings |
| `com.donut.module.modules` | Concrete modules |
| `com.donut.rotation` | Rotation queue, easing, angle math |
| `com.donut.pathfinding` | A* core (pure) + Minecraft glue |
| `com.donut.schematic` | NBT reader, parsers, planner, placement |
| `com.donut.gui.clickgui` | ClickGUI framework and widgets |
| `com.donut.config` | TOML config, profiles, migration |
| `com.donut.event` | Small synchronous event bus |
