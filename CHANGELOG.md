# Changelog

## 1.0.0 (unreleased)

First release, for Minecraft 1.21.1 with NeoForge 21.1.125+ and Create 6.0.0 to 6.0.x.

### Blocks

- **Searchlight**: a long, narrow beam that lights the block it points at (full to 32 blocks, dark by 48).
  Aims where you look when placed; right-click with a Wrench to aim it again.
- **Floodlight**: a wide, short cone (full to 12 blocks, dark by 20) for lighting an area.
- **Pan-Tilt Head**: kinetic mount under a Searchlight or Floodlight. Shaft rotation pans it, an analog
  redstone signal tilts it 6° per level.
- **Rotating Beacon**: two opposite beams that sweep round with shaft speed; lights where they land once
  stopped.
- **Warning Light**: amber lamp with steady, blink, pulse and flash patterns (value panel); the light it
  casts follows the pattern.
- **Headlight**: forward beam for vehicles. On a moving contraption, train or Sable vehicle only the
  Headlights facing the way it travels light up, so they swap when it reverses. Powered at assembly, a
  Headlight stays lit both ways.
- **Tail and Brake Light**: red lamp that brightens for a second when its vehicle slows down.
- **Navigation Light**: red, green or white marker, coloured by how it is placed; the Wrench cycles it.

### Everywhere

- Lights keep landing where they point on Create contraptions, trains and Sable (Create Aeronautics)
  vehicles, measured within 1 block of the drawn beam at 28 blocks a second.
- Redstone dims every light (15 − signal); server config `invertRedstone` makes them light only when
  powered. On contraptions the signal is read at assembly, and Contraption Controls switch lights.
- Dye any light to colour its lens, its beam and the light it casts: a red Searchlight paints a red
  spot. Signal lamps colour the blocks round them too (amber, red, green), placed or on a moving
  contraption, train or Sable vehicle. Drawn by the mod itself, so it
  works with the vanilla renderer and Sodium; off under Iris shader packs, and switchable with the client
  config `colouredLight`. Block light itself stays white (Minecraft has no coloured block light).
- Engineer's Goggles show a light's state, brightness, aim, where its beam lands and its colour, and draw
  beam paths while worn.
- Players without LambDynamicLights still get light from fixed lights (server light blocks) and see
  every beam; with it, moving lights light the world too.
- The visible beam is a square, Minecraft-style truncated pyramid that starts flush on the lens and
  stops where it lands, also where only part of it hits a wall. A beam aimed over open ground still
  lights the ground where its lower edge reaches it. It stays a soft haze when you stand in or
  beside it. Works with Sodium and Iris shaders (fainter with shaders on by default;
  client config `beamVisual` / `beamVisualWithShaders`).
- Recipes, tooltips, a Ponder scene for every block, Sable mass data and our own creative tab.
