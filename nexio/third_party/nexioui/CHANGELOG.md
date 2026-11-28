# Changelog

本项目的所有重要变更都会记录在这里。版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [0.1.1] - 2026-11-19

### 新增

- Slider：`settleToKeyPoints` 锚点吸附模式，抬手、甩动、点按都直接落到最近锚点；手势与键盘逻辑拆成独立 Modifier。
- 下拉菜单：重写弹层布局，接入预测性返回手势。

### 修复

- 下拉退场多留一帧，底部弹窗恢复非莫奈中性底色。
- ArrowPreference 恢复箭头右侧间距。
- Background 恢复调用方传入的 `presetOverride`。
- 仓库更名为 NexioUI，iOS CI 改为拉取 SoftGlass 源码。

## [0.1.0] - 2026-11-18

首个公开版本。

### 新增

- Compose Multiplatform 控件库骨架：`commonMain` 一套源码，支持 Android、Desktop(JVM)、iOS。
- 主题系统 `NexioTheme`：语义色板、文字样式、深色模式、动态取色。
- 按压反馈：全库统一的 AGSL 按压高光 `InteractiveHighlight` 与整块淡色指示器 `NexioIndication`，不使用水波纹。
- 液态玻璃：基于 softglass 的 backdrop 采样，提供按钮、底栏、对话框、底部弹窗等玻璃组件。
- 滚动：弹簧回弹 `NexioOverscrollFactory`、滚动触感、嵌套滚动适配。
- 控件：Card / Button / TextButton / IconButton / Checkbox / Switch / Slider / 分段控件 / 搜索栏 / 数字与日期选择 / 进度条等。
- 弹层：`OverlayDialog`、`OverlayPopup`、`BlurBottomSheet`、下拉菜单，支持预测性返回手势。
- 布局：`Scaffold`（含弹层宿主 `NexioPopupHost`）、顶栏、渐进模糊顶栏、底栏、平板导航。
- 动效：弹簧动画、共享元素形变、弧形弯折、水波纹、背景特效。
- 偏好设置项：Arrow / Checkbox / Switch Preference。
