package com.menglimi.gdust.v2

import android.app.Application
import com.menglimi.gdust.v2.data.Repository

class App : Application() {

    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        repo.store.publishPowerWorkerConfig()
        com.menglimi.gdust.v2.work.PowerWorker.schedule(this)
    }
}
