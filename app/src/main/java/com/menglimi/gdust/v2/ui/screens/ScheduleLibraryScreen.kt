package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.ScheduleCatalog
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ScheduleLibraryScreen(repo: Repository, onBack: () -> Unit, onSelected: () -> Unit) {
    var catalog by remember { mutableStateOf(ScheduleCatalog()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { catalog = withContext(Dispatchers.IO) { repo.store.schedules.snapshot() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "独立课表无法读取，原数据已保留。" }
        finally { busy = false }
    }
    fun select(id: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repo.store.selectSchedule(id) }
                catalog = withContext(Dispatchers.IO) { repo.store.schedules.snapshot() }
                onSelected()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "切换或读取未完成，请核对当前课表。原数据已保留。" }
            finally { busy = false }
        }
    }
    SettingSubPage("课表切换", "独立保存 · 互不覆盖", onBack) {
        SettingGroup {
            SettingOption("学校课表", "学校自动同步始终更新这一份，不改写云端导入的课表。",
                detail = if (catalog.selectedId == ScheduleCatalog.SCHOOL) "当前" else "切换", onClick = { select(ScheduleCatalog.SCHOOL) })
            catalog.schedules.forEach { item ->
                SettingOption(item.name, "${item.schedule.courseCount} 门 · 云端版本 ${item.revision}",
                    detail = if (catalog.selectedId == item.id) "当前" else "切换", onClick = { select(item.id) })
            }
            if (catalog.schedules.isEmpty()) Text("还没有独立课表，可在 MCP 页预览并确认从个人云导入。",
                fontSize = 13.sp, color = LocalDs.current.sub, modifier = Modifier.padding(20.dp))
        }
        error?.let { Text(it, color = LocalDs.current.danger) }
    }
}
