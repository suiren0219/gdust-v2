# 学习助手与宿舍电量

两个独立 Android 应用，分别使用 `chaoxing-core` 与 `power-core`，不依赖原广科课程表 App，也不互相依赖。界面采用 KSU 系的 SukiSU-Ultra Material 方向：实底状态卡、状态徽章、动态配色、深浅主题、AMOLED 纯黑与悬浮玻璃胶囊导航，不需要 root。

| 应用 | 安装标识 | 主要功能 |
|---|---|---|
| 学习助手 | `cn.edu.gdust.suiren.learning` | 学习通正常网页登录、作业筛选、课程任务点进度、原网页详情、本机缓存 |
| 宿舍电量 | `cn.edu.gdust.suiren.power` | 查询并选择已有房间、剩余电量、趋势与历史、后台查询、低电量和掉电通知 |

学习助手与宿舍电量均为 `0.1.2`（versionCode 3），支持 Android 13 及以上。可以同时安装，也可以只安装所需的一个。应用数据、设置、账号会话分别隔离。

宿舍电量 0.1.2 优化多格式响应与电量数值解析，临时网络故障最多补试一次。只有有效余额才能更新本地读数，失败或取消保留原读数与历史；同一宿舍重新确认也保留记录。沿用原包名、加密存储名称与数据格式，使用相同签名覆盖安装即可保留数据。主卡仅按实际余额告警，待更新或刷新失败单独提示。

学习助手 0.1.2 兼容课程页隐藏域的 `id`/`name` 标识，以及实际可信落地 URL 中的课程身份参数；已存在的身份必须一致。修复作业列表 `work/task?...` 相对路径的错误拼接。失败课程默认折叠，已读取的作业优先展示，展开后可单课重试或打开课程原网页。未知或失败课程仍不算“没有作业”。

设置中新增「桌面名称与图标」：支持相册选图、恢复默认、创建或更新自定义桌面快捷方式。默认图标复用主项目同一前景资源和暖奶油底；应用列表名称与安装标识不随快捷方式变化，包名始终保留 `gdust` 与 `suiren`。

关于页署名为「穗稔 · suiren0219」，提供 GitHub 项目主页、发布页和手动检查更新。更新只识别本仓库中对应应用的 `learning-assistant-X.Y.Z[-preview].apk` 或 `dorm-power-X.Y.Z[-preview].apk` 附件，不将主项目或另一应用作为升级。浏览器负责下载，安装由用户确认；尚无对应附件时明确提示未发布。

## 功能边界

学习助手使用通用学习通网页登录，不要求校园门户或教务账号。作业与课程进度分开读取，单课失败保留旧数据并标注；未知页面不能视为“没有作业”。作业状态、截止时间以学习通原页面为准，无自动做题、提交、签到或考试能力。只覆盖核心库已识别的默认课程页与作业协议。

宿舍电量使用用户已有的完美校园电费账号和学校服务代码查询房间。默认学校代码 `1000145` 对应原广科配置，可以修改；账号与代码应以实际学校服务为准。App 保存的是从服务返回的房间选择，不执行远端绑定或充值。日内余额变化包含充值影响，不能等同实际耗电；可用天数为基于本机采样的估算，样本不足不提供虚构结果。

电量后台巡检使用 WorkManager，是否实际请求由核心查询策略判断；系统省电可能延后运行。未绑定或手动模式不安排常规后台查询。通知需要用户授权，通知未能发送时不消耗预警冷却。预警渠道在启动时创建；设置页提供测试通知与系统渠道管理。允许通知权限或从系统设置开启通知后，会复查最近 15 分钟的有效缓存读数，不额外发起查询。测试通知不使用真实电量，也不修改预警冷却。

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

使用 JDK 21、Gradle 9.7.1、Android SDK 37；Java 字节码目标 17，targetSdk 35。先在 `local.properties` 配置 SDK，或设置 `ANDROID_HOME`。

```bash
# 仅学习助手，不加载电量核心或电量 App
 gradle -p independent-apps -PstandaloneApp=learning :chaoxing-core:test :learning-app:testDebugUnitTest :learning-app:assembleDebug :learning-app:assembleRelease

# 仅宿舍电量，不加载学习通核心或学习助手
 gradle -p independent-apps -PstandaloneApp=power :power-core:test :power-app:testDebugUnitTest :power-app:assembleDebug :power-app:assembleRelease
```

本机可用 `D:/android-sdk/gradle-dist/gradle-9.7.1/bin/gradle.bat` 替换 `gradle`。原仓库没有 Gradle wrapper，未伪造 wrapper 文件。

Debug 包名带 `.debug` 后缀。Release 默认生成 unsigned 包；交付的 preview APK 在本机用 Android 开发测试证书单独签名，已经压缩，不含调试预览 Activity。正式发布前应固定专用发布证书；更换证书后无法直接覆盖测试签名版本。签名密钥、构建产物和本地配置不纳入版本控制。

## 隐私和许可

- 查询由设备直接访问对应服务，不经过自建服务器。
- 学习通会话由本 App 的 WebView 管理，不保存输入密码。退出会清除学习通会话与缓存。
- 宿舍绑定、历史及学习通缓存分别保存在本 App 的加密存储中；两个 App 不互读数据。
- 两个 App 均关闭系统备份。换绑、解绑和退出操作提供明确确认。
- 项目遵循上级 [AGPL-3.0 LICENSE](../LICENSE) 和 [NOTICE.md](../NOTICE.md)。应用为个人学习项目，与广东科技学院、超星或完美校园官方无关。

调试合成预览只用于验证界面与交互，明确标注“合成预览”，不是用户真实课程或电量。最终测试结果和未验证范围见 [QA.md](QA.md)。
