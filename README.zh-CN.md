# EmberTimer

**[English](./README.md) | 简体中文**

安卓工作/休息循环计时器,带 GitHub 风格的每日专注热力图。后台可靠运行:精确闹钟、强杀/重启自愈、跨午夜落账。

## 目录

- [背景](#背景)
- [功能](#功能)
- [安装](#安装)
- [使用](#使用)
- [架构](#架构)
- [测试](#测试)
- [致谢](#致谢)
- [许可证](#许可证)

## 背景

番茄工作法类应用的核心矛盾是"后台可靠性":锁屏、强杀、重启、跨午夜都要不丢账。EmberTimer 用前台服务 + 精确闹钟 + 双时钟(单调钟计时/墙钟对账)引擎解决这一问题,并以每日热力图呈现长期专注历史。

## 功能

- 工作/休息双时长自动循环切换,后台持续运行(通知含暂停/跳过/重置三动作与实时倒计时)
- 全历史每日专注热力图:虚拟化懒加载网格,月份标签、图例、连续圆角融合;点击任意日期展开按配置分解的详情卡
- 多套命名时长配置,各自累计总时长
- 暂停/恢复/跳过/重置;暂停后改时长立即按新时长重开
- 时间到声音+震动提醒,自动停止,无需手动关闭
- 强杀后重开应用自动对账恢复;整机重启后自动恢复计时
- 每日累计按 60 秒节奏落账,跨午夜分日记账(误差 <= 60 秒)
- Material You 动态取色(Android 12+,以下回退为烬橙)与 edge-to-edge 布局

## 安装

要求:Android 8.0(API 26)及以上。

- 从 [Releases](https://github.com/Zzz210s/EmberTimer/releases) 下载 APK 安装(调试签名版可直接安装)
- 或从源码构建:

```bash
git clone https://github.com/Zzz210s/EmberTimer.git
cd EmberTimer
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 使用

1. 首次启动授予通知权限
2. (可选)在设置页新建自己的时长配置
3. 主页点"开始"即进入工作/休息循环
4. 阶段结束会有提示音与提醒通知;暂停、跳过、重置可在应用或通知上进行
5. 主页热力图点击任意日期查看当日累计与按配置分解

## 架构

单模块 Compose 应用,分层清晰:

- `timer/` 纯 Kotlin 计时引擎(状态机、checkpoint 对账、双时钟恢复),无 Android 依赖,可 JVM 直测
- `service/` 前台服务(事件驱动:通知/闹钟/提醒/落账)、精确闹钟调度、开机/闹钟接收器
- `data/` Room(profile、daily_total)+ DataStore(设置、运行时状态)
- `ui/` Compose(Material 3):主页(计时卡+热力图)、设置页

计时正确性设计:引擎事件 replay=0 + 订阅握手、单一互斥锁串行化全部驱动路径、结算归属事件携带(抗 RESET/换配置交错)、排空感知的服务拆除。

## 测试

87 个单元测试(JVM + Robolectric)覆盖引擎语义、事件策略、接收器门控、ViewModel 契约:

```bash
./gradlew test
```

## 致谢

- 后台计时可靠性设计参考 [adrcotfas/goodtime](https://github.com/adrcotfas/goodtime)(GPL-3.0)
- 热力图与每日聚合数据模型设计参考 [nsh07/Tomato](https://github.com/nsh07/Tomato)(GPL-3.0)

本项目为独立实现,未复制上述项目源代码(见 [NOTICE](NOTICE))。

## 许可证

[GPL-3.0](LICENSE)
