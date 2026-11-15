package com.menglimi.gdust.v2.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.menglimi.gdust.v2.data.Repository
import java.time.LocalDate

/** 只在前台检查本机月日，不为彩蛋增加闹钟、后台轮询或生日网络请求。 */
@Composable
internal fun BirthdayCelebration(repo: Repository, enabled: Boolean) {
    val version by repo.sessionVersion.collectAsState()
    val owner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    var revision by remember { mutableIntStateOf(0) }
    var visible by remember(version) { mutableStateOf(false) }
    DisposableEffect(repo, owner) {
        val stop = repo.store.observeBirthday { revision++ }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { today = LocalDate.now(); revision++ }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { stop(); owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled, today, revision, version) {
        val birthday = com.menglimi.gdust.v2.data.Birthday.saved(repo.store.birthdayMonthDay)
        if (!enabled || birthday == null || !com.menglimi.gdust.v2.data.Birthday.isToday(birthday, today)) visible = false
        else if (repo.store.birthdayDue(today)) visible = true
    }
    if (enabled && visible) {
        LaunchedEffect(today, version) { repo.store.markBirthdayShown(today) }
        BirthdayDialog(onDismiss = { visible = false })
    }
}
