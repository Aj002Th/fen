# Fen · 分

一个开源、克制的 Android 记账应用。

> 灵感来自 iCost（仅借鉴功能结构与交互思路，不含其代码或素材），
> 目标是砍掉嘈杂功能，把「记一笔」和「看明白钱去哪了」做到极致。

当前状态：**可运行的早期原型**（v0.2.0，现代极简视觉重构）。已打通
记账 → 明细 → 统计 的完整链路，并支持**单层可自定义分类**、搜索与月度汇总轮播。

## 设计语言

冷白底（`#F4F5F7`）+ 纯白卡片 + 近黑文字 + 语义双色（支出珊瑚红 / 收入墨绿）。
金额使用超大粗体等宽数字（`tnum`），其余信息降级为浅灰小字；
圆角统一 24dp，分类图标为开源线性图标（Material Outlined）+ 彩色圆底。


## 技术栈

| 项 | 选择 |
|---|---|
| 语言 / UI | Kotlin 2.4.21 + Jetpack Compose（Material 3） |
| 构建 | AGP 9.4.1 / Gradle 9.8.2 / JDK 17 toolchain |
| minSdk / targetSdk | 26 / 37 |
| 数据 | 手写 SQLite（`FenDb`），金额统一以「分」为 `Long` |
| 架构 | 单 Activity + Compose Navigation + 一个 `FenViewModel` |

选择手写 SQLite 而不是 Room，是为了避开 AGP 9 + Kotlin 2.4 上 KSP 的版本耦合；
等数据结构稳定后再评估迁移。

## 构建与运行

本机 Android 环境的完整说明见 `C:\Android\ANDROID-ENV.md`。

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"
$env:ANDROID_HOME = "C:\Android\sdk"
.\gradlew.bat :app:assembleDebug

# 无头模拟器 + 安装 + 截图
C:\Android\tools\device.ps1 boot
C:\Android\tools\device.ps1 install -Package app.fen
C:\Android\tools\device.ps1 shot
```

## 验收数据（仅 debug）

```powershell
# 生成最近 6 个月约 400 条演示数据（会顺带创建咖啡/宠物/订阅/健身四个分类）
adb shell am start -a android.intent.action.VIEW -d "fen://debug/seed?count=400" -p app.fen
# 清空流水（保留分类）
adb shell am start -a android.intent.action.VIEW -d "fen://debug/clear" -p app.fen
```

开发入口默认隐藏：设置 → 关于 → 连续点标题 10 次才会出现「开发」分组；
App 完全退出后重新进入会恢复隐藏。

## 代码结构

```
app/src/main/java/app/fen/
├─ FenApp.kt                  Application，持有 Repository
├─ MainActivity.kt            单 Activity 入口
├─ data/
│  ├─ Models.kt               Category / Tx / Snapshot
│  ├─ FenDb.kt                SQLiteOpenHelper + 建表 + 种子数据
│  ├─ FenRepository.kt        读写 + 快照刷新
│  ├─ AppPrefs.kt             主题模式等轻量偏好
│  └─ Analytics.kt            月度合计、分类占比、按天分组
├─ ui/
│  ├─ FenRoot.kt              NavHost + 底部导航 + 记账浮层
│  ├─ FenViewModel.kt         单例状态（快照 + 当前月份）
│  ├─ components/             AmountText / KeyPad / 通用小组件
│  ├─ screens/                Bills / AddTransaction / Stats / Settings
│  └─ theme/                  色板、字体、形状
└─ util/Format.kt             金额与日期格式化
```

## 设计原则

1. **金额是主角**：大号等宽数字，其余信息一律降级为浅灰小字。
2. **一屏只做一件事**：明细页看账，统计页看结构，记账页只管输入。
3. **零网络、零账号**：数据只在本机，开机即用。
4. **可解释的默认值**：预置分类，第一次打开就能记账。

## 已实现

- 记账：支出 / 收入、**单层自定义分类**、**时间精确到分钟**、备注、自定义数字键盘（保存再记 / 完成）
- 分类：分类管理页，新增 / 编辑名称、图标、颜色；有流水的分类禁止删除
- 编辑：点击明细里的记录即可修改金额 / 分类 / 时间 / 备注
- 主题：跟随系统 / 浅色 / 深色，设置页可切换
- 明细：**三页汇总轮播**、按月切换、**按天卡片**、日小计、**常驻搜索**、长按删除
- 统计：按分类聚合的环形图、分类排行、近 7 天柱状图
- 设置：右上角入口（所有页面可达），分类管理、深色模式与关于

## Roadmap

- [x] 支出 / 收入记账，**单层可自定义分类**、日期、备注、自定义键盘
- [x] 按天卡片的明细、月度汇总轮播、常驻搜索
- [x] 环形图 + 分类排行 + 近 7 天柱状图
- [x] 现代极简视觉 + 线性图标体系
- [x] 统一页头、右上角设置入口、页面转场
- [x] 深色模式
- [ ] 键盘四则运算、`记录` 连续记账的动效与反馈
- [x] 编辑已有记录
- [x] 分类管理与单层自定义分类
- [ ] 滑动删除 + 撤销
- [ ] 预算设置页
- [ ] 日历视图
- [ ] 导出 / 导入（CSV、JSON）、本地备份
- [ ] 主题色自定义、字号设置
- [ ] 单元测试（Analytics、金额解析）与截图回归

## License

待定（建议 Apache-2.0 或 MIT）。



