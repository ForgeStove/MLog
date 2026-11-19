![icon.png](src/main/resources/icon.png)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.265-F16436)
![License](https://img.shields.io/badge/License-GPLv3-blue)

**English** | [简体中文](README.zh_CN.md)

Mindustry's logic system, rebuilt for Minecraft: a graphical statement canvas and editor, plus the instruction set behind them.

Programs are assembled from statement cards and run by a processor on the server. A program can read and write variables, link nearby blocks, read block and unit state, control units, draw on logic displays, and exchange data with memory cells and banks.

## Features

### Blocks

| Block | Details |
| --- | --- |
| Micro Processor | Runs 6 instructions per tick by default; `setrate` can lower this. Link range is 10 blocks on each axis. |
| World Processor | Requires game master permission. Runs 24 instructions per tick by default, up to 3000. No link range limit, and can link blocks that a micro processor cannot. |
| Tiled Logic Display | Adjacent cells facing the same way form one canvas, drawn with `draw` and `drawflush`. |
| Memory Cell | Stores 64 values. |
| Memory Bank | Stores 512 values. |
| World Cell | Stores 512 values; only a world processor can read or write it. |

### Statements

- Control: `set`, `op`, `jump`, `wait`, `end`, `stop`, `setrate`
- Values: `select`, `lookup`, `format`, `packcolor`, `unpackcolor`
- Blocks and links: `sensor`, `getlink`, `read`, `write`, `control`
- Printing: `print`, `printchar`, `printflush`
- Displays: `draw`, `drawflush`
- Units: `ubind`, `ucontrol`, `uradar`, `ulocate`
- World processor only: `setprop`, `query`, `getblock`, `setblock`, `getflag`, `setflag`, `message`, `playsound`, `playmusic`, `effect`, `spawn`, `status`, `weathersense`, `weatherset`, `explosion`, `localeprint`

### Editor

- Drag statement cards to reorder; jump targets are drawn as curves.
- The add-statement dialog is sorted by category and can be searched.
- Variables, effects, colors, and options each have their own dialog.
- Linked blocks get names such as `block1`, `cell1`, and `bank1`, which can be used directly as variables.

### Compatibility

- Create: output to display links, value settings and filters, and link coordinates that follow contraption and schematic transforms.
- Sable: sublevel coordinate conversion for links and sensors.

Both are optional.

## Requirements

### Dependencies

- Minecraft 1.21.1
- NeoForge 21.1.265

### Optional

- Create 6.0.10
- Sable 2.0.6

## Credits

- Authors: ForgeStove, zhouzhouwu
- Credits: cym, CMS, [Mindustry](https://github.com/Anuken/Mindustry), AnvilCraft

## License

GPLv3, see [LICENSE](LICENSE).
