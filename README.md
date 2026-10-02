# BlueMapSignMarkers

This plugin replicates Dynmap sign markers.  
Compatible with Paper / Folia.

## Setup

Build with `gradlew build` (needs JDK 17 or newer, any version) and put `build/libs/SignMarkers-<version>.jar` into `plugins`  
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
resolution, and shrink/grow as you zoom out/in. Tune `scaling.*` in `plugins/BlueMapSignMarkers/config.yml`, then run
`/bluemap reload`. The plugin installs a small script + style into `bluemap/web/signmarkers/` on every BlueMap (re)load.
Markers created by older versions are converted automatically.

## Example

This will create a "TestTest" marker on the map with `bluemap/web/markers/icon_name.png` as icon.  
![Demo Image](demo.png)  

## Download

Download on [Modrinth](https://modrinth.com/plugin/bluemapsignmarkers)
