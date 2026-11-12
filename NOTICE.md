# NOTICE — 第三方代码与许可

本项目整体以 **GNU Affero General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

## 从 Nexio课程表 移植的 UI 实现

新版界面（导航骨架、主题 tokens、底栏、课表/今日等页面版式）**直接移植自 Nexio课程表**：

- 上游项目：Nexio课程表（作者：HaoZai000 / haooz）
- 上游许可：GNU AGPL-3.0（与本项目一致）
- 参考包：`Nexio课程表 1.7.0-1113`（versionCode 170，package `com.haooz.chedule`，内含 revision `28a99a3b23458a0d39741b9841c9755f5374fba2`）
- 参考源码：本地检出 `NexioSchedule`（HEAD `c540e9a`，早于上述包）用于对照写法

移植方式：把上游 Compose 页面与组件源码抄入本项目（`ui/` 下对应文件），仅替换数据来源与业务调用（原项目用 KMP `:core` + ViewModel，本项目用 `Repository` + `Store`）。UI 结构、版式参数与动效节奏保持上游实现。

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
