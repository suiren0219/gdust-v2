package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs

/** 作者署名与主页（改这里一处即可）。 */
internal const val APP_AUTHOR = "穗稔"
internal const val APP_GITHUB = "https://github.com/suiren0219"

/** 设置二级页通用骨架：同款左缘右滑返回手势。 */
@Composable
internal fun SettingSubPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val ds = LocalDs.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 96.dp.toPx() }
    var backPx by remember { mutableStateOf(0f) }
    androidx.activity.compose.BackHandler { onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { start -> backPx = if (start.x < 120f) 0f else Float.NaN },
                    onDragEnd = {
                        if (!backPx.isNaN() && backPx >= threshold) onBack()
                        backPx = 0f
                    },
                    onDragCancel = { backPx = 0f },
                ) { change, dragAmount ->
                    if (!backPx.isNaN()) backPx += dragAmount
                    change.consume()
                }
            },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = title, subtitle = subtitle, onBack = onBack)
        content()
        Spacer(Modifier.height(110.dp))
    }
    }
}

/** 个人信息：本机身份快照（不做任何编辑上传，全部只读展示）。 */
@Composable
fun ProfileScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val store = repo.store
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0-alpha"
        }.getOrDefault("2.0.0-alpha")
    }
    SettingSubPage("个人信息", "本机账号与数据快照", onBack) {
        SectionHeader("身份")
        SettingGroup {
            SettingOption(title = store.realName.ifEmpty { "未登录" })
            SettingDivider()
            SettingOption(title = "学号", detail = store.jobNumber.ifEmpty { "—" })
            SettingDivider()
            SettingOption(title = "登录状态", detail = if (store.hasSession) "有效" else "未登录")
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("数据")
        SettingGroup {
            SettingOption(title = "课表来源", detail = when (store.scheduleSource) {
                "zf_term" -> "教务整学期"; "portal_week" -> "门户本周"; else -> "未同步"
            })
            SettingDivider()
            SettingOption(title = "本地缓存课程", detail = "${store.loadBlocks().size} 张卡")
            SettingDivider()
            SettingOption(title = "校历", detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步")
            SettingDivider()
            SettingOption(
                title = "水电绑定",
                detail = if (store.hasPowerBinding) store.powerRoomName else "未绑定",
            )
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("隐私")
        SettingGroup {
            Text(
                "所有数据只存本机加密存储：登录凭证、水电绑定、课表缓存。\n无自建服务器、无账号体系、无遥测；密码不落盘。",
                fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "版本 $version · 作者 $APP_AUTHOR",
            fontSize = 11.sp, color = ds.sub,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).wrapContentSize(Alignment.Center),
        )
    }
}

/** 关于与更新：版本信息 + 更新说明 + 数据链路。 */
@Composable
fun AboutScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val pkgInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "2.0.0-alpha"
    val versionCode = pkgInfo?.let {
        if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode
    } ?: 1L
    SettingSubPage("关于与更新", "版本 · 更新说明 · 数据链路", onBack) {
        val hostContext = androidx.compose.ui.platform.LocalContext.current
        SectionHeader("版本")
        SettingGroup {
            SettingOption(title = "作者", detail = APP_AUTHOR)
            SettingDivider()
            SettingOption(
                title = "GitHub 主页",
                detail = "github.com/suiren0219",
                onClick = {
                    runCatching {
                        hostContext.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(APP_GITHUB),
                            )
                        )
                    }.onFailure {
                        // 没有浏览器时兜底：复制链接
                        val cm = hostContext.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", APP_GITHUB))
                    }
                },
            )
            SettingDivider()
            SettingOption(title = "当前版本", detail = versionName)
            SettingDivider()
            SettingOption(title = "内部版本号", detail = versionCode.toString())
            SettingDivider()
            SettingOption(title = "数据链路", detail = "本机直连")
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("本版更新")
        SettingGroup {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateItem("真实学期课表", "教务学期导入按每门课的实际周次保存，分段开课、单双周、调课独立保留")
                UpdateItem("电量图表", "走势图加坐标轴刻度与时间轴、预警阈值虚线，点按查看任意采样点")
                UpdateItem("登录一次保持", "门户与教务会话本机加密保存；断网不掉登录，只有学校端失效才要求重登")
                UpdateItem("交互", "课表左右滑翻周、设置左滑返回、电费下拉刷新、启动页自选、莫奈取色")
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("数据链路")
        SettingGroup {
            Text(
                "课表/校历/通知 → 学校门户（smart-admin-api）\n整学期课表 → 正方教务（经 WebVPN 或校内网）\n水电 → 完美校园智能水电接口\nAI 总结 → 你自填的 OpenAI 兼容端点",
                fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "广科课程表 · 由 $APP_AUTHOR 制作 · AGPL-3.0\n界面移植自 HaoZai000/NexioSchedule 1.7.0-1113",
            fontSize = 11.sp, color = ds.sub,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
private fun UpdateItem(title: String, desc: String) {
    val ds = LocalDs.current
    Column {
        Text("• $title", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ds.text)
        Text(desc, fontSize = 11.sp, lineHeight = 15.sp, color = ds.sub, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
    }
}
