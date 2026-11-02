<p align="center">
  <b>English</b> · <a href="README_RU.md">Русский</a>
</p>

# Mogged for NeoForge

A port of [Mogged](https://github.com/Cranch-fur/Mogged) to NeoForge. It does the
same things: lets you switch between your own account skin and two custom presets with a single key, with a smooth animated transition. HD skins, capes and elytra, a built-in skin editor, and other players who have the mod see your look too.

The only difference from the Fabric mod: no [Flashback](https://modrinth.com/mod/flashback) support, as that replay mod
exists for Fabric only.

## Requirements

| Minecraft | NeoForge | Java |
|---|---|---|
| 1.20.1 | 47.1+ | 17 |
| 1.21.1 | 21.1+ | 21 |
| 26.1.x | 26.1+ | 25 |
| 26.2 | 26.2+ | 25 |
| 26.3 | 26.3+ | 25 |

Each version needs its own jar. To let other players see your presets, put the same jar on the server. A vanilla server
works too, but then only you see them. Fabric, Forge and NeoForge players don't see each other's presets: the loaders use
different network channels.

## Building

Any JDK 17+ starts Gradle; the JDK a version needs (17, 21 or 25) is downloaded automatically.

```sh
./gradlew build            # Minecraft 26.2 (default)
./gradlew build -Pmc=26.1
./gradlew build -Pmc=26.3
./gradlew build -Pmc=1.21.1
./gradlew build -Pmc=1.20.1
```

On Windows use `gradlew.bat`. The jar ends up in `build/libs/`, for example `mogged-1.1.0+mc26.2-neoforge.jar`. For
1.20.1 there is also a jar with Mojang names in `build/devlibs/`, for development only: put the jar from `build/libs/` in
the game.

Run the game from the workspace with `./gradlew runClient -Pmc=<version>`.

## Project layout

```
src/main/java/mogged          shared code: protocol and the server-side relay
src/client/java/mogged/client shared client code (as in the Fabric mod)
  ├─ gui                      screens, widgets and the skin editor
  ├─ render                   CPU model renderer for previews
  └─ compat                   a Flashback stand-in (there is none on NeoForge)
src/mc26, src/mc1211, src/mc1201
                              per-version code: the "platform" classes (networking, drawing, input, textures),
                              the mixins into player skin lookup and the NeoForge event glue (mogged/neoforge)
versions/                     per-Minecraft-version build settings; "family" picks the folder above
```

Only the loader glue and networking differ from the Fabric mod: `mogged/neoforge` (the `@Mod` entry points and NeoForge
events), `platform/ModNetworking` and `client/platform/ClientNetworking` (NeoForge payloads; on 1.20.1, where NeoForge
is still Forge 47, Forge channels), `MoggedClient` (no Fabric API) and `SkinServer` (its events moved out). Everything
else (skins, the animation, screens, the editor) is copied as is.

Settings are stored in `config/mogged.json`, in the Fabric mod's format.

## Authors

@cranch_fur and @murkatel.

## License

Copyright (C) 2026 @cranch_fur, @murkatel

Mogged is free software under the [GNU General Public License v3.0](LICENSE).
