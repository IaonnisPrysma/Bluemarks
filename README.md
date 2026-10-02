# Bluemarks

Bluemarks adds Dynmap-style sign markers to BlueMap.  
Compatible with Paper and Folia (Minecraft 1.19.x and newer).

> Bluemarks is a renamed, modified fork of **BlueMapSignMarkers** by **kugge**, see [Credits](#credits).

## Setup

Build with `gradlew build` (needs JDK 17 or newer, any version) and put `build/libs/Bluemarks-<version>.jar` into `plugins`  
Create `markers` directory inside `bluemap/web` directory.  
Put marker icons into `bluemap/web/markers`.  
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

## Example

This will create a "TestTest" marker on the map with `bluemap/web/markers/icon_name.png` as icon.  
![Demo Image](demo.png)  

## Credits

- **BlueMapSignMarkers** by **kugge**: the original plugin this project is forked from.
  [Source](https://github.com/KaiijuMC/BlueMapSignMarkers) · [Modrinth](https://modrinth.com/plugin/bluemapsignmarkers)
- **[BlueMap](https://bluemap.bluecolored.de/)** by Blue (BlueColored): the map and the API this plugin builds on.
- **[Dynmap](https://github.com/webbukkit/dynmap)**: the sign marker behaviour this plugin replicates.
- **iaonnis**: icon scaling, Folia restoration and the rename to Bluemarks, built on top of the original.

## License
GPL-3.0, see [LICENCE.md](LICENCE.md). As required by the GPL, this is a modified version of the original work.
