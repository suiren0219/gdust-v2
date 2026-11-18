package com.menglimi.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.chaoxing.ChxCourse
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.KIcon
import com.menglimi.study.learning.LearningLibrary
import com.menglimi.study.learning.LearningScope
import com.menglimi.study.learning.explanation
import com.menglimi.study.learning.libraryKey

/** 分类只整理本机视图与读取范围；依据与“待确认”理由始终写在课程卡上。 */
@Composable
fun CourseLibraryCard(
    library: LearningLibrary,
    courseCount: Int,
    visibleCount: Int,
    onSelectScope: (LearningScope, String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onImportCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    KCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("课程分类", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$visibleCount / $courseCount 门", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ScopeChip("全部课程", library.scope == LearningScope.ALL) { onSelectScope(LearningScope.ALL, null) }
            library.folders.forEach { folder ->
                ScopeChip(folder.name, library.scope == LearningScope.FOLDER && library.selectedId == folder.id) {
                    onSelectScope(LearningScope.FOLDER, folder.id)
                }
            }
            library.terms.forEach { term ->
                ScopeChip(term.name, library.scope == LearningScope.TERM && library.selectedId == term.id) {
                    onSelectScope(LearningScope.TERM, term.id)
                }
            }
            if (library.terms.isNotEmpty()) {
                ScopeChip("开学日前", library.scope == LearningScope.BEFORE_START) {
                    onSelectScope(LearningScope.BEFORE_START, null)
                }
            }
            if (library.folders.isNotEmpty() || library.terms.isNotEmpty()) {
                ScopeChip("待确认", library.scope == LearningScope.UNASSIGNED) {
                    onSelectScope(LearningScope.UNASSIGNED, null)
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = { folderName = ""; creating = true }) { Text("新建文件夹") }
            if (library.folders.isNotEmpty()) TextButton(onClick = { managing = true }) { Text("管理") }
            TextButton(onClick = onImportCalendar) { Text(if (library.terms.isEmpty()) "导入校历" else "更新校历") }
        }
        Text(scopeHint(library), fontSize = 12.sp, lineHeight = 19.sp, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))
    }
    if (creating) {
        AlertDialog(onDismissRequest = { creating = false },
            title = { Text("新建文件夹") },
            text = { Column {
                Text("名称 1–30 个字符；手动归类优先于按个人开学日自动判断。", fontSize = 13.sp, lineHeight = 20.sp)
                OutlinedTextField(folderName, { folderName = it }, singleLine = true, label = { Text("文件夹名称") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } },
            confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = {
                onCreateFolder(folderName.trim()); creating = false
            }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } })
    }
    if (managing) {
        AlertDialog(onDismissRequest = { managing = false },
            title = { Text("管理文件夹") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (library.folders.isEmpty()) Text("还没有文件夹。", fontSize = 13.sp)
                library.folders.forEach { folder ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { deleting = folder.id }) { Text("删除") }
                    }
                }
                Text("删除文件夹只会解除归类，不会删除学习通里的课程。", fontSize = 12.sp, lineHeight = 19.sp,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } },
            confirmButton = { TextButton(onClick = { managing = false }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { managing = false; folderName = ""; creating = true }) { Text("新建") } })
    }
    deleting?.let { id ->
        val folder = library.folders.find { it.id == id }
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("删除「${folder?.name ?: "文件夹"}」？") },
            text = { Text("该文件夹里的 ${library.assignments.count { it.value == id }} 门课程会回到按个人开学日自动判断。") },
            confirmButton = { TextButton(onClick = { onRemoveFolder(id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

/** 课程卡上的归类入口；选择后立即保存，不触发任何网络请求。 */
@Composable
fun CourseClassifyDialog(
    course: ChxCourse,
    library: LearningLibrary,
    onMove: (String, String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("归类课程") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Text(course.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(library.explanation(course), fontSize = 12.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            val current = library.folder(course.libraryKey())?.id
            FolderRow("不放入文件夹（按加入时间判断校历）", current == null) {
                onMove(course.libraryKey(), null); onDismiss()
            }
            library.folders.forEach { folder ->
                FolderRow(folder.name, current == folder.id) {
                    onMove(course.libraryKey(), folder.id); onDismiss()
                }
            }
            TextButton(onClick = { creating = true }) { Text("新建文件夹…") }
            Text("选“不放入文件夹”不会清除个人开学日自动判断的结果。", fontSize = 12.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
    if (creating) {
        AlertDialog(onDismissRequest = { creating = false },
            title = { Text("新建文件夹") },
            text = { OutlinedTextField(folderName, { folderName = it }, singleLine = true,
                label = { Text("文件夹名称") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = {
                onCreateFolder(folderName.trim()); creating = false
            }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } })
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun FolderRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (selected) KIcon(Glyph.CHECK, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
    }
}

private fun scopeHint(library: LearningLibrary): String = when (library.scope) {
    LearningScope.ALL -> "筛选只影响课程列表与本轮作业读取；切换范围不会清掉已有结果。"
    LearningScope.FOLDER -> "手动归类优先于自动学期，刷新课程不会覆盖你的选择。"
    LearningScope.TERM -> if (library.terms.isEmpty()) "尚未导入个人开学日期，无法按学期筛选。"
        else "课程按加入日期落在本学期开学日与下一开学日之间来归类；最早开学日前单独列出。"
    LearningScope.BEFORE_START -> "这里是加入日期早于最早个人开学日的课程。"
    LearningScope.UNASSIGNED -> "这里是没有手动文件夹、也无法按已导入开学日归入学期的课程。"
}
