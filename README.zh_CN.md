![icon.png](src/main/resources/icon.png)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.265-F16436)
![License](https://img.shields.io/badge/License-GPLv3-blue)

[English](README.md) | **简体中文**

[Mindustry](https://github.com/Anuken/Mindustry) 的逻辑系统，在 Minecraft 中重建：图形化的语句画布与编辑器，以及配套的指令集。

用语句卡片编写程序，由服务端的处理器执行。程序可以读写变量、链接附近的方块、读取方块与单位的状态、控制单位、在逻辑显示屏上绘图，还能和内存元、内存库交换数据。

## 功能

### 方块

| 方块 | 说明 |
| --- | --- |
| 微型逻辑处理器 | 默认每 tick 执行 6 条指令，可用 `setrate` 调低。链接范围为各轴 10 格。 |
| 世界处理器 | 需要游戏管理员权限。默认每 tick 执行 24 条指令，最高 3000 条。没有链接距离限制，还能连接微型逻辑处理器无法连接的方块。 |
| 逻辑显示单元 | 朝向相同的相邻单元会拼成一块画布，用 `draw` 和 `drawflush` 绘制。 |
| 内存元 | 可存 64 个值。 |
| 内存库 | 可存 512 个值。 |
| 世界内存元 | 可存 512 个值，只有世界处理器能读写。 |

### 语句

- 控制：`set`、`op`、`jump`、`wait`、`end`、`stop`、`setrate`
- 数值：`select`、`lookup`、`format`、`packcolor`、`unpackcolor`
- 方块与链接：`sensor`、`getlink`、`read`、`write`、`control`
- 打印：`print`、`printchar`、`printflush`
- 显示：`draw`、`drawflush`
- 单位：`ubind`、`ucontrol`、`uradar`、`ulocate`
- 世界处理器专用：`setprop`、`query`、`getblock`、`setblock`、`getflag`、`setflag`、`message`、`playsound`、`playmusic`、`effect`、`spawn`、`status`、`weathersense`、`weatherset`、`explosion`、`localeprint`

### 编辑器

- 拖动语句卡片即可调整顺序，跳转目标之间画成曲线。
- 添加语句的对话框按分类排列，也可以搜索。
- 变量、效果、颜色、选项各有一个对话框。
- 把方块链接到处理器后，每个链接会得到 `block1`、`cell1`、`bank1` 这样的名称，可直接作为变量使用。

### 兼容

- Create：把文本输出到显示链接器，能读写元件的数值设置和过滤器；链接坐标会随结构与蓝图一起变换。
- Sable：处理链接和传感器在子层级中的坐标换算。

两者都是可选依赖。

## 环境要求

### 依赖

- Minecraft 1.21.1
- NeoForge 21.1.265

### 可选

- Create 6.0.10
- Sable 2.0.6

## 鸣谢

- 作者：ForgeStove、zhouzhouwu
- 鸣谢：cym、CMS、[Mindustry](https://github.com/Anuken/Mindustry)、AnvilCraft

## 许可

GPLv3，见 [LICENSE](LICENSE)。
