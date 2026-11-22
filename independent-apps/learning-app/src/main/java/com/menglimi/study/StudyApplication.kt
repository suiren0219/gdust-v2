package com.menglimi.study

import android.app.Application
import com.menglimi.ksu.ui.AppearanceStore
import com.menglimi.study.learning.LearningController

/** 惰性初始化：合成预览不接触正式学习缓存、Cookie 或网络。 */
class StudyApplication : Application() {
    val learning by lazy { LearningController(this) }
    val appearance by lazy { AppearanceStore(this) }
}
