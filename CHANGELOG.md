# Changelog

## 1.0.1 (unreleased)

- The Mods screen's website link now opens the mod's CurseForge page (it opened the GitHub repository); bugs and
  suggestions still go to GitHub Issues.
- A turning Rotating Beacon or Pan-Tilt Head no longer draws its beam a tick ahead of the light it casts: the lit spot
  stays at the beam's end instead of trailing behind it.
- With a shader pack on and no dynamic lights mod, a light on a vehicle, a contraption or a turning head lights nothing
  but its beam. The first time one shines, a chat tip now says that LambDynamicLights or Sodium Dynamic Lights fixes it
  (once a session; client config `shaderHint` turns it off).

## 1.0.0 (2026-11-05)

First release, for Minecraft 1.21.1 with NeoForge 21.1.125+ and Create 6.0.0 to 6.0.x.

### Blocks

- **Searchlight**: a long, narrow beam that lights the block it points at (full to 32 blocks, dark by 48).
  Placed level, facing away from you; hold right-click on its Pan and Tilt boxes to aim it, to the degree.
- **Floodlight**: a wide, short cone (full to 12 blocks, dark by 20) for lighting an area; standing still it
  lights several spots, so it follows notches and uneven walls.
- **Pan-Tilt Head**: kinetic mount under a Searchlight, Floodlight or Headlight. Shaft rotation pans it, an analog
  redstone signal tilts it 6° per level. Its value box makes it sweep back and forth, up to 180° either way of
  where it points when set.
- **Rotating Beacon**: two opposite beams that sweep round with shaft speed; lights where they land once
  stopped.
- **Warning Light**: amber lamp with steady, blink, opposite blink, pulse, double flash and strobe patterns,
  always or only at night (value box); the light it casts follows the pattern.
- **Headlight**: forward beam for vehicles. On a moving contraption, train or Sable vehicle only the
  Headlights facing the way it travels light up, so they swap when it reverses. Powered at assembly, a
  Headlight stays lit both ways.
- **Tail and Brake Light**: red lamp that brightens for a second when its vehicle slows down.
- **Edge Light**: low marker for runways, taxiways and platforms. Dye colours its lens, redstone dims it, you can
  walk and roll over it, and it moves with contraptions and Sable vehicles. Nothing ticks, so it can be placed by
  the hundred; a recipe makes eight.

### Everywhere

- Colour and the lit square land on slabs, stairs, carpet, snow, paths and farmland, and follow blocks as they are placed and broken.
- A beam lights everything inside it: block light goes across the whole lit surface, not only where rays land.
- Create's Clipboard copies settings between lights: aim, dye colour, a Warning Light's pattern, a head's sweep.
- Lights keep landing where they point on Create contraptions, trains and Sable (Create Aeronautics)
  vehicles, measured within 1 block of the drawn beam at 28 blocks a second.
- Redstone dims every light (15 − signal); server config `invertRedstone` makes them light only when
  powered. On contraptions the signal is read at assembly, and Contraption Controls switch lights.
- Dye any light to colour its lens, its beam and the light it casts: a red Searchlight paints a red
  spot, and overlapping colours add up: red and green make yellow. Signal lamps colour the blocks round them too (amber, red), placed or on a moving
  contraption, train or Sable vehicle. Drawn by the mod itself, so it
  works with the vanilla renderer and Sodium; off under Iris shader packs, and switchable with the client
  config `colouredLight`. Block light itself stays white (Minecraft has no coloured block light).
- Looking into a beam, its lens glows softly round it (client config `lensGlow`).
- Standing inside a beam, a faint haze of its light fills the view, stronger towards the lamp (client config `insideHaze`).
- A beam stops on a Sable vehicle in its way instead of shining through it, and its lit square and colour are painted on
  the vehicle and move with it (the vehicle's blocks get no block light).
- Engineer's Goggles show a light's state and brightness.
- Moving lights light the world with LambDynamicLights or Sodium Dynamic Lights, also under shader packs. Without either,
  fixed lights still give light (server light blocks), every beam is drawn, and moving lights paint a brighter spot with a glow round it.
- The visible beam is a square, Minecraft-style truncated pyramid that starts flush on the lens and
  stops where it lands, also where only part of it hits a wall. A beam aimed over open ground still
  lights the ground where its lower edge reaches it. It stays a soft haze when you stand in or
  beside it. Works with Sodium and Iris shaders (fainter with shaders on by default;
  client config `beamVisual` / `beamVisualWithShaders`).
- Recipes, tooltips, a Ponder scene for every block, Sable mass data and our own creative tab.
