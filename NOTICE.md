# NOTICE — 第三方代码与许可

本项目为**闭源自用**软件（见 [LICENSE](LICENSE)），当前不对外分发。

## 从 Nexio课程表 移植的 UI 实现

新版界面（导航骨架、主题 tokens、底栏、课表/今日等页面版式）移植自 Nexio课程表：

- 上游项目：Nexio课程表（作者：HaoZai000 / haooz）
- 上游许可：GNU AGPL-3.0
- 参考包：`Nexio课程表 1.7.0-1113`（versionCode 170，package `com.haooz.chedule`，内含 revision `28a99a3b23458a0d39741b9841c9755f5374fba2`）
- 精确参考源码：[HaoZai000/NexioSchedule](https://github.com/HaoZai000/NexioSchedule/tree/28a99a3b23458a0d39741b9841c9755f5374fba2)，对应上述 APK revision；先前 `c540e9a` 仅用于移植组件底座对照
- 授权状态：**已与上游作者沟通，同意本项目先以闭源形式自用**。各移植文件头保留来源与 revision 注释作为出处记录
- 如日后对外分发：需先取得上游的书面授权豁免，或整体按 AGPL-3.0 合规发布（恢复 AGPL LICENSE 并附来源说明）

移植范围：新版 `LiquidBottomTabs`、`CapsuleSlider`、`CapsuleSelector`、SoftGlass 样式及交互 helpers 直接移植并适配；今日、课表、设置参照该提交的 Compose 页面和组件重写数据接线。保留广科的 `Repository` / `Store`、实际排课与导航契约。电费和待办为使用同一组件体系的本地页面。课表卡当前采用无材质着色路径，未接入上游 `GlassSurface`；本项目不宣称全页面像素级复刻。

## 其他依赖

| 依赖 | 许可 |
|---|---|
| [softglass](https://github.com/HaoZai000/softglass) | Apache-2.0 |
| [Glasmio](https://github.com/neoruaa/Glasmio) | Apache-2.0 |
| [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) | Apache-2.0 |
| [MaterialKolor](https://github.com/jordond/MaterialKolor) | MIT |
| AndroidX / Jetpack Compose / Material3 | Apache-2.0 |
| OkHttp / OkHttp-SSE / Gson | Apache-2.0 |
| ZXing core | Apache-2.0 |
