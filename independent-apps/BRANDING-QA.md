# 两款独立 App 0.1.1 品牌与更新验收

## 工作范围

工作树：`D:/projects/gdust/gdust-v2-standalone-branding`，分支 `feat/standalone-branding-updates`，基线 `4a3f918`。

从 `gdust-v2-standalone-apps` 复制 independent-apps、chaoxing-core、power-core 的源码，并逐文件核对 SHA-256；未复制构建、发行包、配置或签名文件，未修改原工作树。源码尚未提交、推送或发布。

## 修改

- 两款均升为 versionName `0.1.1`、versionCode `2`。
- 安装标识保留 `cn.edu.gdust.suiren.learning` 和 `cn.edu.gdust.suiren.power`，均含学校与作者署名。
- 关于页显示「穗稔 · suiren0219」，包含项目主页、GitHub 发布页、检查更新。
- 更新请求为公开 GitHub Release 列表；仅接受本仓库、对应应用、已上传、非 draft 的版本化 APK 附件。不会将主项目、另一款 App、debug 或 unsigned 文件当作升级。下载使用浏览器，由用户决定安装。
- 默认 adaptive icon 的前景与主项目 `ic_launcher_foreground.webp` 字节一致，暖奶油底为 `#FAE7D8`。前景 SHA-256：`9014826e09f6fa7ec2708d4e240d6595758dd74c6ae314813cfcd028aaf78204`。
- 设置新增桌面名称、相册选图、恢复默认、应用到桌面；通过 ShortcutManager 创建或更新固定 ID 的自定义快捷方式，不修改安装身份或应用列表名称。

## 构建及单测

JDK 21、Gradle 9.7.1、SDK 37，Debug 与压缩 Release 均构建成功，Release lintVital 通过。

共 24 组、200 项单测：原有核心/宿主 194 项，加共用更新筛选、数字版本比较、来源校验、另一应用隔离、桌面名称边界等 6 项。0 failures、0 errors、0 skipped。

上次任务被中断时 Release 尚未生成，本次补跑至 BUILD SUCCESSFUL 后签名，未将中断构建视为通过。

## 模拟器

`emulator-5554 / farm_api36`，未操作真机或 5556。

- 两款分别先安装旧 0.1.0，再使用 `adb install -r` 覆盖新版，均成功冷启动。
- 两款关于页署名、版本与实际包名显示正确。
- 两款点击检查更新均成功获取公开列表，显示最近发布中没有本应用安装包。只读核对当前发布附件确实只有主项目 alpha.21/alpha.17，不存在独立应用包。
- 学习助手：系统确认添加快捷方式；修改为测试名称 Suiren-Study 后，dumpsys shortcut 确认固定 ID 的 shortLabel/longLabel 已更新且启动目标仍是本应用。
- 学习助手：使用模拟器生成的纯色测试图，通过系统照片选择器导入，预览显示自定义图标，更新已有快捷方式成功；随后恢复默认名称与图标并重新应用。
- 宿舍电量：默认图标预览与主项目一致，系统确认添加桌面入口成功。两款复用同一桌面组件，选图/改名逻辑相同。
- 未使用真实学习通或电费账号执行在线业务；未重新验证长期后台提醒。
- 本轮未重新进行大字号/深色设备验收。
- 结束时停止两款应用并返回桌面，未改变系统字号、分辨率或主题。

截图和临时图均在 `independent-apps/build/branding-qa/`，不入库。

## 签名与交付

使用与旧 0.1.0 样品相同的 Android 开发测试证书签名精简 Release；不含 debug 预览 Activity。证书 SHA-256 为 `47e8918b6a2f26e55efb013e21c0548a47154fbdf03b3133b44f1fde1857c6ee`。两包 apksigner v3 校验和 zipalign 检查均通过。正式发布仍需固定正式证书；更换证书无法覆盖此测试签名。

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| dist/learning-assistant-0.1.1-preview.apk | 3203611 | 5c9669f57a4e226c68e5d53ed1f913b7e507af59022a4da62e747f4ab34f245b |
| dist/dorm-power-0.1.1-preview.apk | 3274070 | 5c935afd20752b622ee9718da49d8d173b9b5e480b04fb50aa705da3269d857e |

## QQ 交付状态

用户已授权发到 QQ「我的手机」。当前电脑自动化仅发现 D:/QQ.exe 的扫码登录窗口，没有可操作的已登录聊天窗口，未确认这两份 0.1.1 文件已发送。等待用户打开已登录的「我的手机」后继续。未重复发送此前传输状态不明的 0.1.0/服务状态包。
