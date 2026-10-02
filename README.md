# Bluemarks

Bluemarks adds two kinds of markers to BlueMap:

- **Sign markers**: Dynmap-style markers created by placing a `[map]` sign.
- **Nether portal markers**: a toggleable "Nether Portals" layer that follows portals as they are lit, found and broken.

Compatible with Paper and Folia (Minecraft 1.19.x and newer).

> Bluemarks combines and modifies **BlueMapSignMarkers** (kugge) and **BlueMapPortalMarkers** (Max Aldis), see [Credits](#credits).

## Setup

Build with `gradlew build` (needs JDK 17 or newer, any version) and put `build/libs/Bluemarks-<version>.jar` into `plugins`  
On first start Bluemarks creates `bluemap/web/markers` and copies its bundled icon (`nether-portal.webp`) into it; existing files are never overwritten.  
Put your own marker icons into `bluemap/web/markers`.  
Supported formats: png, webp, gif, jpg/jpeg, svg.  

## How to use
Place a sign.

- 1st line: "[map]"
- 2nd line: text
- 3rd line: text
- 4th line: image name (extension optional, e.g. `icon_name` or `icon_name.webp`)

If the file `bluemap/web/markers/icon_name.<ext>` exists, the sign and BlueMap markers will update.  
The marker is removed when the sign is destroyed.

## Icon scaling
Icons are drawn at a fixed height (`icon-size` in `config.yml`, default 32px) regardless of the source image's
resolution, and shrink/grow as you zoom out/in. Tune `scaling.*` in `plugins/Bluemarks/config.yml`, then run
`/bluemap reload`. The plugin installs a small script + style into `bluemap/web/bluemarks/` on every BlueMap (re)load.
Markers created by older versions are converted automatically.

## Upgrading from BlueMapSignMarkers
Remove the old jar and install Bluemarks. On first start, `config.yml` and the `marker-set-*.json` files from
`plugins/BlueMapSignMarkers/` are copied into `plugins/Bluemarks/` (the old folder is left untouched), and existing
markers keep working. The old `bluemap/web/signmarkers/` folder is no longer used and can be deleted.

Coming from BlueMapPortalMarkers: remove it and its `my-custom-script.js` / `my-custom-style.css` entries from
`webapp.conf` (Bluemarks ships that fix itself). Copy `plugins/BlueMapPortalMarkers/portals.json` to
`plugins/Bluemarks/portals.json` to keep your known portals, and `/bmportals` is now `/bluemarks portals`.

## Nether portal markers
Enabled by default (`portals.*` in `config.yml`). They use the same icon pipeline as sign markers: the icon is
`bluemap/web/markers/nether-portal.webp` (change it with `portals.icon`), drawn at `icon-size` and zoom-scaled by `scaling.*`.
Click a portal for its coordinates and, with `portals.linking.enabled`, its predicted Overworld/Nether counterpart
(vanilla 8:1 rule) with a "Go to linked portal" link. The link works out of the box, no `webapp.conf` editing needed.

How portals are found: lighting a portal and breaking one are tracked live; existing portals are found with Paper's
point-of-interest API, around spawn and online players at startup, when chunks load, and (optionally) periodically.
**That API needs Paper 26.1+.** On older servers only portals lit or broken while the server runs are tracked.
Portals are stored in `plugins/Bluemarks/portals.json`.

### Commands
Permission: `bluemarks.admin` (default: op).

| Command | Description |
| --- | --- |
| `/bluemarks portals reload` | Re-read `config.yml` and apply it live. |
| `/bluemarks portals sweep [radius]` | Sweep around every world spawn and online player. |
| `/bluemarks portals sweep me [radius]` / `<player> [radius]` | Sweep around you / a player. |
| `/bluemarks portals sweep <x> [y] <z>` | Full-height sweep at coordinates in your world (Y is ignored). |
| `/bluemarks portals stats` | Total and per-world portal counts. |
| `/bluemarks portals purge [world]` | Remove stored portals (one world or all) and their markers. Useful after removing portals with WorldEdit. |

## Example

This will create a "TestTest" marker on the map with `bluemap/web/markers/icon_name.png` as icon.  
![Demo Image](demo.png)  

## Credits

- **BlueMapSignMarkers** by **kugge**: the original plugin this project is forked from.
  [Source](https://github.com/KaiijuMC/BlueMapSignMarkers) · [Modrinth](https://modrinth.com/plugin/bluemapsignmarkers)
- **BlueMapPortalMarkers** by **Max Aldis**: the nether portal markers (detection, clustering, linking, store, commands)
  were merged in from it. [Source](https://github.com/maldis018/BlueMapPortalMarkers) · [Modrinth](https://modrinth.com/plugin/bluemapportalmarkers)
- **[BlueMap](https://bluemap.bluecolored.de/)** by Blue (BlueColored): the map and the API this plugin builds on.
- **[Dynmap](https://github.com/webbukkit/dynmap)**: the sign marker behaviour this plugin replicates.
- **iaonnis**: icon scaling, Folia restoration, the rename to Bluemarks and the merge of the portal markers, built on top of the originals.

## License
GPL-3.0, see [LICENCE.md](LICENCE.md). Both original plugins are GPL-3.0 too (BlueMapPortalMarkers: Copyright © 2026 Max Aldis).
As required by the GPL, this is a modified version of the original works. Files ported from BlueMapPortalMarkers
carry a notice at the top (`dev.iaonnis.bluemarks.portals`).
