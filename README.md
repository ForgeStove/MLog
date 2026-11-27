![icon.png](src/main/resources/icon.png)

[![CurseForge](https://img.shields.io/curseforge/dt/1702964?logo=curseforge&label=&suffix=%20&style=flat&color=242629&labelColor=e04e14&logoColor=1c1c1c)](https://www.curseforge.com/projects/1702964)
[![Modrinth](https://img.shields.io/modrinth/dt/mlogic?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5ca424&logoColor=1c1c1c)](https://modrinth.com/mod/mlogic)
[![License](https://img.shields.io/github/license/ForgeStove/MLog?style=flat&color=900c3f)](https://github.com/ForgeStove/MLog/blob/main/LICENSE)

**English** | [简体中文](README.zh_CN.md)

MLog brings Mindustry's logic system to Minecraft: programs are written with statement cards and run automatically by a processor.

A program can read and write variables and perform calculations; it can also link nearby blocks, read block and unit state, control blocks and units, draw on logic displays, or exchange data with Memory Cells, Memory Banks, and even other processors.

## Quick Start

1. Place a Micro Processor and right-click it to open the editor.
2. Place a sign next to it, link it to the processor with the editor's link tool, and note its name, `sign1`.
3. Add these two statements and save. The processor starts running:

```text
print "Hello, MLog!"
printflush sign1
```

By default a processor runs 6 instructions per tick. When the program reaches the end, it loops back to the start.

## Features

### Editor

Drag cards to reorder statements; jump targets are connected by curves. The add-statement dialog is grouped by category and can be searched. Variables, effects, colors, and options each have their own dialog.

### Compatibility

- Create: text output to display links, reading and writing value settings and filters, and link coordinates that follow contraption and schematic transforms.
- Sable: sublevel coordinate conversion for links and sensors.

## Blocks

| Block | Details |
| --- | --- |
| Micro Processor | Runs 6 instructions per tick by default; `setrate` can lower it. Links reach 10 blocks along each axis. |
| World Processor | Requires game master permission. Runs 24 instructions per tick by default, up to 3000. Has no link range limit and can link blocks that a Micro Processor cannot. |
| Tiled Logic Display | Adjacent cells facing the same direction form one canvas, drawn with `draw` and `drawflush`. |
| Memory Cell | Holds 64 values. |
| Memory Bank | Holds 512 values. |
| World Cell | Holds 512 values; only a World Processor can read or write it. |

## Statements

<details>
<summary>Show the full list</summary>

- Control: `set`, `op`, `jump`, `wait`, `end`, `stop`, `setrate`
- Values: `select`, `lookup`, `format`, `packcolor`, `unpackcolor`
- Blocks and links: `sensor`, `getlink`, `read`, `write`, `control`
- Printing: `print`, `printchar`, `printflush`
- Displays: `draw`, `drawflush`
- Units: `ubind`, `ucontrol`, `uradar`, `ulocate`
- World processor only: `setprop`, `query`, `getblock`, `setblock`, `getflag`, `setflag`, `message`, `playsound`, `playmusic`, `effect`, `spawn`, `status`, `weathersense`, `weatherset`, `explosion`, `localeprint`

</details>

## Requirements

Requires Minecraft 1.21.1 and NeoForge 21.1.265 on both client and server. Create 6.0.10 and Sable 2.0.6 are optional.

## Credits

- Authors: ForgeStove, zhouzhouwu
- Credits: cym, CMS, [Mindustry](https://github.com/Anuken/Mindustry), AnvilCraft

## License

GPLv3, see [LICENSE](LICENSE).
