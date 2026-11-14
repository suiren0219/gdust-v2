# NOTICE — 第三方代码与许可

本项目以 **GNU AGPL-3.0** 发布（见 [LICENSE](LICENSE)）。

## 从 Nexio课程表 移植的 UI 实现（历史）

早期界面（导航骨架、主题 tokens、玻璃底栏、课表/今日等页面版式）移植自 Nexio课程表：

- 上游项目：Nexio课程表（作者：HaoZai000 / haooz）
- 上游许可：GNU AGPL-3.0
- 参考包：`Nexio课程表 1.7.0-1113`（versionCode 170，package `com.haooz.chedule`，内含 revision `28a99a3b23458a0d39741b9841c9755f5374fba2`）
- 精确参考源码：[HaoZai000/NexioSchedule](https://github.com/HaoZai000/NexioSchedule/tree/28a99a3b23458a0d39741b9841c9755f5374fba2)，对应上述 APK revision；先前 `c540e9a` 仅用于移植组件底座对照
- 授权状态：移植阶段已与上游作者沟通，曾以闭源形式自用。现仓库整体按 AGPL-3.0 开源发布，相关提交保留在 git 历史中并按 AGPL-3.0 提供，移植文件头保留来源与 revision 注释
- CZeroX 改版（`feat/czero-ui`）已删除 `LiquidBottomTabs`、`CapsuleSlider`、`CapsuleSelector`、SoftGlass 等移植实现，当前界面为独立重写

移植范围（历史）：`LiquidBottomTabs`、`CapsuleSlider`、`CapsuleSelector`、SoftGlass 样式及交互 helpers 直接移植并适配；今日、课表、设置参照该提交的 Compose 页面和组件重写数据接线。保留广科的 `Repository` / `Store`、实际排课与导航契约。电费和待办为使用同一组件体系的本地页面。课表卡采用无材质着色路径，未接入上游 `GlassSurface`；本项目不宣称全页面像素级复刻。

## 其他依赖

| 依赖 | 许可 |
|---|---|
| [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) | Apache-2.0 |
| [HyperNotification](https://github.com/xzakota/HyperNotification) | Apache-2.0 |
| [MaterialKolor](https://github.com/jordond/MaterialKolor) | MIT |
| AndroidX / Jetpack Compose / Material3 | Apache-2.0 |
| OkHttp / OkHttp-SSE / Gson | Apache-2.0 |
| ZXing core | Apache-2.0 |
| [capsule](https://github.com/Kyant0/capsule) / [shapes](https://github.com/Kyant0/shapes)（vendor Miuix 仍使用） | Apache-2.0 |
