# 学习助手与宿舍电量

两个独立 Android 应用，分别使用 `chaoxing-core` 与 `power-core`，不依赖原广科课程表 App，也不互相依赖。界面采用 KSU 系的 SukiSU-Ultra Material 方向：实底状态卡、状态徽章、动态配色、深浅主题、AMOLED 纯黑与悬浮玻璃胶囊导航，不需要 root。

| 应用 | 安装标识 | 主要功能 |
|---|---|---|
| 学习助手 | `cn.edu.gdust.suiren.learning` | 学习通正常网页登录、作业筛选、课程任务点进度、原网页详情、本机缓存 |
| 宿舍电量 | `cn.edu.gdust.suiren.power` | 查询并选择已有房间、剩余电量、趋势与历史、后台查询、低电量和掉电通知 |

学习助手与宿舍电量支持 Android 13 及以上，可以同时安装，也可以只安装所需的一个。应用数据、设置、账号会话分别隔离。当前源码候选为学习助手 `0.1.13`（versionCode 14）与宿舍电量 `0.1.10`（versionCode 11）；本地 alpha.26 发布附件仍为学习助手 `0.1.13` 与宿舍电量 `0.1.9`。alpha.27 的宿舍电量 Release 尚未构建，当前已验证的是 Debug 构建与单测；alpha.27 的 Release 签名、附件、设备覆盖安装、数据保留和真实业务均未验证/发布。

宿舍电量 0.1.3 增加本机加密快照备份恢复，隔离损坏统计/历史项及无效时区字段；0.1.6 修复升级后裁剪旧采样和账本字段遗漏；0.1.8 将查询策略改为仅标记本机读数“待更新”，不会因该设置自动联网。开机单次查询为独立开关且默认关闭。沿用旧包名、存储与签名；覆盖安装保留数据，卸载或清除数据会删除本机记录。

宿舍电量 0.1.6 修复升级后刷新裁剪旧采样，以及不同 0.1.4 功能包之间遗漏账本字段的问题。历史默认显示全部，不再自动删掉 30 天前的采样；恢复电费账本的读取、编辑、保存及同绑定备份缺失字段回填。开机单次查询仍为默认关闭，不增加常驻或周期轮询。

宿舍电量 0.1.8（versionCode 9）将「查询节奏」明确改名为「更新提示节奏」。手动 / 均衡 / 低电量关注 / 自定提示间隔只控制本地读数何时显示“待更新”；设置页说明该选项本身不会联网。开机单次查询由独立开关控制，默认关闭。

宿舍电量 0.1.2 优化多格式响应与电量数值解析，临时网络故障最多补试一次。只有有效余额才能更新本地读数，失败或取消保留原读数与历史；同一宿舍重新确认也保留记录。沿用原包名、加密存储名称与数据格式，使用相同签名覆盖安装即可保留数据。主卡仅按实际余额告警，待更新或刷新失败单独提示。

学习助手 0.1.2 兼容课程页隐藏域的 `id`/`name` 标识，以及实际可信落地 URL 中的课程身份参数；已存在的身份必须一致。修复作业列表 `work/task?...` 相对路径的错误拼接。失败课程默认折叠，已读取的作业优先展示，展开后可单课重试或打开课程原网页。未知或失败课程仍不算“没有作业”。

设置中新增「桌面名称与图标」：支持相册选图、恢复默认、创建或更新自定义桌面快捷方式。默认图标复用主项目同一前景资源和暖奶油底；应用列表名称与安装标识不随快捷方式变化，包名始终保留 `gdust` 与 `suiren`。

关于页署名为「穗稔 · suiren0219」，提供 GitHub 项目主页、发布页和手动检查更新。已发布 Release 中，学习助手附件必须使用与 APK `versionName` 完全对应的 `learning-assistant-X.Y.Z.apk` 名称，宿舍电量则使用 `dorm-power-X.Y.Z.apk`；alpha.26 当前附件为 `learning-assistant-0.1.13.apk` 与 `dorm-power-0.1.9.apk`，alpha.27 源码候选分别为 `learning-assistant-0.1.13.apk` 与 `dorm-power-0.1.10.apk`，后者待 Release 验证。`versionCode` 是安装包元数据，不写入附件名；预览附件仅在版本号后加 `-preview`。更新器只识别本仓库对应应用的匹配附件，不将主项目或另一应用作为升级。浏览器负责下载，安装由用户确认；尚无对应的已上传附件时明确提示未发布。

## 功能边界

学习助手使用通用学习通网页登录，不要求校园门户或教务账号。作业与课程进度分开读取，单课失败保留旧数据并标注；未知页面不能视为“没有作业”。作业状态、截止时间以学习通原页面为准，无自动做题、提交、签到或考试能力。只覆盖核心库已识别的默认课程页与作业协议。

宿舍电量使用用户已有的完美校园电费账号和学校服务代码查询房间。默认学校代码 `1000145` 对应原广科配置，可以修改；账号与代码应以实际学校服务为准。App 保存的是从服务返回的房间选择，不执行远端绑定或充值。日内余额变化包含充值影响，不能等同实际耗电；可用天数为基于本机采样的估算，样本不足不提供虚构结果。

开机自动查询使用一次性 WorkManager 任务，默认关闭；开启后仅在开机或本应用更新时查询一次，结束后不安排下一轮。通知需要用户授权，通知未能发送时不消耗预警冷却。预警渠道在启动时创建；设置页提供测试通知与系统渠道管理。允许通知权限或从系统设置开启通知后，会复查最近 15 分钟的有效缓存读数，不额外发起查询。测试通知不使用真实电量，也不修改预警冷却。

## 工程

```text
independent-apps/
  learning-app/       学习助手 Android 宿主与界面
  power-app/          宿舍电量 Android 宿主与界面
  ksu-ui/             共用主题、卡片、图标、导航；无业务依赖
../chaoxing-core/     学习通 Kotlin/JVM 核心
../power-core/        电量 Kotlin/JVM 核心
```

两个 App 各自声明业务依赖，不通过同一个 APK 的构建变体隐藏另一功能。`standaloneApp` 属性进一步允许只配置一个 App 和对应核心库。

使用 JDK 21、Gradle 9.7.1、Android SDK 37；Java 字节码目标 17，targetSdk 35。先在 `local.properties` 配置 SDK，或设置 `ANDROID_HOME`。本仓库没有 Gradle Wrapper；将 `GRADLE_HOME` 设为 Gradle 安装目录的绝对路径，然后在仓库根目录用 PowerShell 按绝对路径调用 `gradle.bat`：

```powershell
$gradle = Join-Path $env:GRADLE_HOME 'bin\gradle.bat'

# 仅学习助手，不加载电量核心或电量 App
& $gradle -p independent-apps -PstandaloneApp=learning :chaoxing-core:test :learning-app:testDebugUnitTest :learning-app:assembleDebug :learning-app:assembleRelease

# 仅宿舍电量，不加载学习通核心或学习助手
& $gradle -p independent-apps -PstandaloneApp=power :power-core:test :power-app:testDebugUnitTest :power-app:assembleDebug :power-app:assembleRelease
```

主工程与学习助手共用 `chaoxing-core/build/`，两个独立 App 也共用 `ksu-ui` 的构建目录。主工程和两个独立 App 的 Gradle 调用须顺序执行，上一条结束后再启动下一条。

Debug 包名默认带 `.debug` 后缀。两个独立 App 都没有产品风味，`:assembleRelease` 直接构建 `release`，启用 R8 和资源压缩。签名配置有效时产物为：

- 学习助手：`independent-apps/learning-app/build/outputs/apk/release/learning-app-release.apk`
- 宿舍电量：`independent-apps/power-app/build/outputs/apk/release/power-app-release.apk`

未提供 Release 签名配置时，产物在相同目录，文件名以 `-release-unsigned.apk` 结尾。签名通过本机 Gradle 用户配置提供 `releaseKeyStore`、`releaseStorePassword`、`releaseKeyAlias` 和 `releaseKeyPassword`，不将配置值、密钥或构建产物纳入版本控制。

历史 `-preview` 附件是用别名为 `androiddebugkey` 的开发测试密钥签过的 Release APK，不是 Debug 产物。本轮两个独立 App 的 Release 已沿用历史附件的同一 `androiddebugkey` 密钥，公开证书摘要比对一致；设备原位升级与数据保留尚未实测。别名相同不等于证书相同，新生成的 `androiddebugkey` 不能替代历史密钥。

实际产出 APK 才算已构建，完成 APK 签名才算已签名。发布前仍须核对包名、`versionName` 和 `versionCode`，用 `apksigner` 验签并与历史附件比对证书摘要，再实测覆盖安装及数据保留。只有核验后的签名 APK 附件已上传到已发布的 GitHub Release，才算已发布。

## 隐私和许可

- 查询由设备直接访问对应服务，不经过自建服务器。
- 学习通会话由本 App 的 WebView 管理，不保存输入密码。退出会清除学习通会话与缓存。
- 宿舍绑定、历史及学习通缓存分别保存在本 App 的加密存储中；两个 App 不互读数据。
- 两个 App 均关闭系统备份。换绑、解绑和退出操作提供明确确认。
- 项目遵循上级 [AGPL-3.0 LICENSE](../LICENSE) 和 [NOTICE.md](../NOTICE.md)。应用为个人学习项目，与广东科技学院、超星或完美校园官方无关。

调试合成预览只用于验证界面与交互，明确标注“合成预览”，不是用户真实课程或电量。2026-11-17 / 0.1.0 的历史验收结果见 [QA.md](QA.md)；本轮候选结果与未验证范围见 [docs/FEATURES_AND_APIS.md](../docs/FEATURES_AND_APIS.md)。
