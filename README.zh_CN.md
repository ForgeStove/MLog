![icon.png](src/main/resources/icon.png)

[![CurseForge](https://img.shields.io/curseforge/dt/1702964?logo=curseforge&label=&suffix=%20&style=flat&color=242629&labelColor=e04e14&logoColor=1c1c1c)](https://www.curseforge.com/projects/1702964)
[![Modrinth](https://img.shields.io/modrinth/dt/mlog?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5ca424&logoColor=1c1c1c)](https://modrinth.com/mod/mlog)
[![License](https://img.shields.io/github/license/ForgeStove/MLog?style=flat&color=900c3f)](https://github.com/ForgeStove/MLog/blob/main/LICENSE)

[English](README.md) | **简体中文**

MLog 把 Mindustry 的逻辑系统移植到 Minecraft：用语句卡片编写程序，处理器会自动反复执行。

程序可以读写变量、执行运算，也能链接附近的方块、读取方块和单位的状态、控制方块和单位、在逻辑显示屏上绘图，或是与内存元、内存库，甚至是其他处理器交换数据。

## 快速开始

1. 放置一个微型逻辑处理器，右键打开编辑器。
2. 放一个告示牌，用编辑器的链接功能把它接到处理器上，链接名是 `sign1`。
3. 添加下面两条语句并保存，处理器就会开始运行：

```text
print "Hello, MLog!"
printflush sign1
```

处理器默认每 tick 执行 6 条指令。程序执行到末尾后会回到开头继续运行。

## 功能

### 编辑器

拖动卡片调整语句顺序，jump 目标之间用曲线连接；添加语句的对话框按分类排列，也支持搜索。变量、效果、颜色和选项各有独立的对话框。

### 兼容

- Create：把文本输出到显示链接器，读写元件的值设置和过滤槽；链接坐标会随装置和蓝图一起变换。
- Sable：为链接和传感器处理物理结构中的坐标换算。

## 方块

| 方块           | 说明                                                                                                                   |
|----------------|------------------------------------------------------------------------------------------------------------------------|
| 微型逻辑处理器 | 默认每 tick 执行 6 条指令，可以用 `setrate` 调低。链接范围是每个轴 10 格。                                             |
| 世界处理器     | 需要游戏管理员权限。默认每 tick 执行 24 条指令，最高 3000 条。没有链接距离限制，也能连接微型逻辑处理器不能连接的方块。 |
| 逻辑显示单元   | 朝向相同的相邻单元拼成一块画布，用 `draw` 和 `drawflush` 绘制。                                                        |
| 内存元         | 可存 64 个值。                                                                                                         |
| 内存库         | 可存 512 个值。                                                                                                        |
| 世界内存元     | 可存 512 个值，只有世界处理器能读写。                                                                                  |

## 语句

<details>
<summary>展开完整列表</summary>

- 控制：`set`、`op`、`jump`、`wait`、`end`、`stop`、`setrate`
- 数值：`select`、`lookup`、`format`、`packcolor`、`unpackcolor`
- 方块与链接：`sensor`、`getlink`、`read`、`write`、`control`
- 打印：`print`、`printchar`、`printflush`
- 显示：`draw`、`drawflush`
- 单位：`ubind`、`ucontrol`、`uradar`、`ulocate`
- 世界处理器专用：`setprop`、`query`、`getblock`、`setblock`、`getflag`、`setflag`、`message`、`playsound`、`playmusic`、`effect`、`spawn`、`status`、
  `weathersense`、`weatherset`、`explosion`、`localeprint`

</details>

## 环境

需要 Minecraft 1.21.1 和 NeoForge 21.1.265。Create 6.0.10 和 Sable 2.0.6 为可选兼容模组。

## 鸣谢

- 作者：ForgeStove、zhouzhouwu
- 鸣谢：cym、CMS、[Mindustry](https://github.com/Anuken/Mindustry)、AnvilCraft

## 许可

GPLv3，见 [LICENSE](LICENSE)。
