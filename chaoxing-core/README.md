# chaoxing-core

独立的 Kotlin/JVM 学习通作业模块，包名 `com.menglimi.chaoxing`。读取课程、任务点进度、作业列表和截止时间，返回结构化结果，不依赖原应用的仓储、待办或学校教务。

- 整体读取：`ChxWorkReader(ChxApi(...)).read()`，返回逐课程成功或失败。
- 局部读取：`ChxApi.courses/courseTabs/workList/workDetailHtml`。
- 课程进度：`ChxCourseApi(...).load()`。
- 传输：注入 `suspend (Request) -> ChxResponse`，或使用 `ChxOkHttpTransport(cookieJar)`。

宿主负责正常网页登录、CookieJar、账号切换与缓存生命周期、界面。Cookie 候选不等于已认证；未知版式不能当成空列表。读取范围和会话契约见 [完整模块设计](../docs/FEATURE_MODULES.md#学习通作业模块)。

在仓库根目录运行：

```bash
gradle -p standalone :chaoxing-core:test :chaoxing-core:jar
gradle -p examples/chaoxing-work run
```

[最小接入示例](../examples/chaoxing-work/src/main/kotlin/example/chaoxing/Main.kt) 展示公开 API 的完整读取链路，默认只使用合成响应。模块及源码复用沿用仓库的 [LICENSE](../LICENSE) 与 [NOTICE](../NOTICE.md)。
