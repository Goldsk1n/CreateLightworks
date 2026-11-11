# Create: Lightworks

Functional lighting for [Create](https://modrinth.com/mod/create): searchlights, floodlights, rotating
beacons, warning lights, edge markers and vehicle lights that keep landing where they point on Create contraptions,
trains and [Create Aeronautics](https://modrinth.com/mod/create-aeronautics) vehicles.

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-lightworks). Changes: [CHANGELOG.md](CHANGELOG.md).
**Community and support:** [Discord](https://discord.gg/HAtMB7UQss). Bugs: [GitHub Issues](https://github.com/Goldsk1n/CreateLightworks/issues).

## Blocks

| Block | What it does | Controls |
| --- | --- | --- |
| Searchlight | Long, narrow beam: full to 32 blocks, dark by 48 | Pan and Tilt boards, redstone, dye |
| Floodlight | Wide cone: full to 12 blocks, dark by 20; lights several spots when fixed | Pan and Tilt boards, redstone, dye |
| Pan-Tilt Head | Kinetic mount: shaft pans the light on top, full circle or sweeping back and forth; analog redstone tilts it 6° per level | Shaft speed, redstone, value box |
| Rotating Beacon | Two opposite beams sweeping with shaft speed | Shaft speed, redstone, dye |
| Warning Light | Amber lamp: steady, blink, opposite blink, pulse, double flash or strobe, always or only at night | Value box, redstone, dye |
| Headlight | Forward beam; on a moving vehicle only the leading ones light | Redstone at assembly keeps it lit both ways |
| Tail and Brake Light | Red lamp; brightens for a second when the vehicle slows | Redstone, dye |
| Edge Light | Low marker for runways, taxiways and platforms; no collision, nothing ticks, eight from a recipe | Redstone, dye |

Redstone dims every light (brightness = 15 − signal); the server config `invertRedstone` makes lights
off unless powered. Dyed lights and signal lamps colour what they light (client config `colouredLight`;
Edge Lights only colour their own lens). Engineer's Goggles show a light's state. Every block has a recipe,
a tooltip and a Ponder scene.

## How light reaches the world

- **Fixed lights** place invisible light blocks where the beam lands, several for a wide beam (server side,
  so it works for every player).
- **Moving lights** (on contraptions, trains, Sable vehicles, or on a turning Pan-Tilt Head) light the world
  on each client: through [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) or [Sodium Dynamic Lights](https://modrinth.com/mod/sodium-dynamic-lights) if installed,
  otherwise through an invisible light block in that client's world only (client config `clientLightBlocks`).
  Light blocks go into water too, so beams light ponds and the sea.
- **Every beam** is also drawn as a faint square cone, with a lit square where it lands. Client config: `beamVisual`,
  `beamVisualWithShaders`.

## Shader packs

Under an Iris shader pack, beams, lens glows and coloured light are drawn once the pack has finished the picture, so
they look as they do without one (client config `beamsAfterShaderPack`). Tested with Complementary Reimagined, BSL,
MakeUp Ultra Fast, Solas, Bliss and Photon.

## Requirements

| | Versions |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.125 or newer |
| Create | 6.0.0 or newer, below 6.1.0 |
| Optional | LambDynamicLights 4+ or Sodium Dynamic Lights (client), Sable 1.0.4+ with Create Aeronautics |

Tested with Sodium 0.8, Iris 1.8, Entity Culling, ImmediatelyFast, Sodium Extra, Contraption Lights and Create: Train
Lights. Create: Dynamic Lights cannot be installed next to
LambDynamicLights, so it is not supported.

## Building

Requires JDK 21.

```bash
./gradlew build               # jar in build/libs; runs unit, architecture and content tests
./gradlew runData             # regenerate src/generated (models, lang, recipes, Ponder schematics)
./gradlew runGameTestServer   # headless game tests
./gradlew runClient           # dev client
```


## Bugs and suggestions

On [GitHub Issues](https://github.com/Goldsk1n/CreateLightworks/issues).

## Licence

[MIT](LICENSE).
