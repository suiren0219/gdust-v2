package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.pageBackground

/** 作者署名与主页（改这里一处即可）。 */
internal const val APP_AUTHOR = "穗稔"
internal const val APP_GITHUB = "https://github.com/suiren0219/gdust-v2"
internal const val SCHOOL_SITE = "https://www.gdust.edu.cn"

internal fun openProjectHomepage(context: android.content.Context) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(APP_GITHUB)))
    }.onFailure {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("GitHub", APP_GITHUB))
        android.widget.Toast.makeText(context, "项目地址已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
}

internal fun openSchoolSite(context: android.content.Context) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SCHOOL_SITE)))
    }.onFailure {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("学校官网", SCHOOL_SITE))
        android.widget.Toast.makeText(context, "官网地址已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 设置二级页通用骨架：同款左缘右滑返回手势。 */
@Composable
internal fun SettingSubPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }

    Box(Modifier.fillMaxSize().settingsEdgeBack(onBack)) {
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground())
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppUiTokens.PagePadding),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = title, subtitle = subtitle, onBack = onBack)
        content()
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
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
    val scope = rememberCoroutineScope()
    var birthdayReading by remember { mutableStateOf(false) }
    var birthdayStatus by remember { mutableStateOf(if (store.birthdayMonthDay.isBlank()) "尚未从教务获取" else "已保存生日月日") }
    SettingSubPage("个人信息", "账号与本机数据", onBack) {
        SettingsSectionTitle("身份")
        AccountIdentityCard(repo)
        SettingsSectionCard {
            SettingOption(title = "生日祝福", subtitle = "仅保存月日，生日当天祝福一次。", detail = birthdayStatus)
            SettingEditor {
                Button(if (birthdayReading) "正在读取…" else "从教务读取生日", enabled = !birthdayReading) {
                    birthdayReading = true
                    scope.launch {
                        try {
                            birthdayStatus = if (repo.refreshTeachingBirthday()) "已保存生日月日" else "教务未提供可识别的出生日期"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } finally {
                            birthdayReading = false
                        }
                    }
                }
            }
        }
        SettingsSectionTitle("数据")
        SettingsSectionCard {
            SettingOption(title = "课表来源", detail = when (store.scheduleSource) {
                "zf_term" -> "教务整学期"; "portal_week" -> "门户本周"; else -> "未同步"
            })
            SettingsSectionDivider()
            SettingOption(title = "本地缓存课程", detail = "${store.loadBlocks().size} 张卡")
            SettingsSectionDivider()
            SettingOption(title = "校历", detail = store.loadCalendar()?.let { "第 ${it.week}/${it.allWeek} 周" } ?: "未同步")
            SettingsSectionDivider()
            SettingOption(
                title = "水电绑定",
                detail = if (store.hasPowerBinding) store.powerRoomName else "未绑定",
            )
        }
        SettingsSectionTitle("隐私")
        SettingsSectionCard {
            Text(
                "所有数据只存本机加密存储：登录凭证、水电绑定、课表缓存。\n无自建服务器、无账号体系、无遥测；密码不落盘。",
                fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
                modifier = Modifier.padding(AppUiTokens.CardPadding),
            )
        }
        Text(
            "版本 $version · 作者 $APP_AUTHOR",
            fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
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
        SettingsSectionTitle("版本")
        SettingsSectionCard {
            SettingOption(title = "作者", detail = APP_AUTHOR)
            SettingsSectionDivider()
            SettingOption(
                title = "项目主页",
                subtitle = "GitHub · 源码、反馈与 Star 支持",
                onClick = { openProjectHomepage(hostContext) },
            )
            SettingsSectionDivider()
            SettingOption(
                title = "学校官网",
                subtitle = "广东科技学院 · gdust.edu.cn",
                onClick = { openSchoolSite(hostContext) },
            )
            SettingsSectionDivider()
            SettingOption(title = "当前版本", detail = "$versionName ($versionCode)")
        }
        SettingsSectionTitle("本版更新")
        SettingsSectionCard {
            Column(Modifier.padding(AppUiTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateItem("真实学期课表", "教务学期导入按每门课的实际周次保存，分段开课、单双周、调课独立保留")
                UpdateItem("电量图表", "走势图加坐标轴刻度与时间轴、预警阈值虚线，点按查看任意采样点")
                UpdateItem("登录一次保持", "门户与教务会话本机加密保存；断网不掉登录，只有学校端失效才要求重登")
                UpdateItem("交互", "课表左右滑翻周、设置右滑返回、电费下拉刷新、启动页自选")
            }
        }
        SettingsSectionTitle("数据链路")
        SettingsSectionCard {
            Text(
                "课表/校历/通知 → 学校门户（smart-admin-api）\n整学期课表 → 正方教务（经 WebVPN 或校内网）\n水电 → 完美校园智能水电接口\nAI 总结 → 你自填的 OpenAI 兼容端点",
                fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
                modifier = Modifier.padding(AppUiTokens.CardPadding),
            )
        }
        Text(
            "广科课程表 · 由 $APP_AUTHOR 制作 · AGPL-3.0 开源\n个人项目，与广东科技学院官方无关 · 界面组件来自 Miuix",
            fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
private fun UpdateItem(title: String, desc: String) {
    val ds = LocalDs.current
    Column {
        Text("• $title", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ds.text)
        Text(desc, fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
    }
}
