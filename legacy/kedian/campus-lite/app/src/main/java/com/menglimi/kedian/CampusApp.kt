package com.menglimi.kedian

import android.app.Application
import com.menglimi.kedian.learning.LearningController
import com.menglimi.kedian.power.PowerController
import com.menglimi.kedian.power.PowerScheduling
import com.menglimi.kedian.ui.AppearanceStore

class CampusApp : Application() {
    val learning by lazy { LearningController(this) }
    val power by lazy { PowerController(this) }
    val appearance by lazy { AppearanceStore(this) }

    override fun onCreate() {
        super.onCreate()
        PowerScheduling.ensure(this)
    }
}
