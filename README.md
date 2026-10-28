# Create: Lightworks (working title)

Functional lighting for [Create](https://modrinth.com/mod/create): searchlights, beacons and vehicle
lights that keep working on Create contraptions, trains and
[Create Aeronautics](https://modrinth.com/mod/create-aeronautics) vehicles.

> Status: tech spike. Nothing here is playable yet.

## Requirements

| | Versions |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.125 or newer |
| Create | 6.0.0 or newer, below 6.1.0 |
| Optional | Sable 1.0.4+ with Create Aeronautics, LambDynamicLights |

## Building

Requires JDK 21.

```bash
./gradlew build          # jar in build/libs
./gradlew test           # unit and architecture tests, no game boot
./gradlew runGameTestServer   # headless game tests
./gradlew runClient      # dev client
```

Dev runtime profiles, hot-swap and other options are described in the workspace `CLAUDE.md`
(`-Prun_profile=latest|lite|floor`, `-Pdev_perf=true`).

## Licence

Code: [MIT](LICENSE). Assets are our own; this project contains no Create Aeronautics assets or code.
