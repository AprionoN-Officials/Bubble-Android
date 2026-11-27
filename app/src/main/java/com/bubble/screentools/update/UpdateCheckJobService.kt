package com.bubble.screentools.update

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight Android JobService checking GitHub Releases periodically when network constraints
 * match user settings (Wi-Fi Only / Any network). Uses zero foreground services and zero wake loops.
 */
class UpdateCheckJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        activeJob = scope.launch {
            val manager = UpdateManager(applicationContext)
            val result = manager.checkForUpdates(manual = false)
            val needsReschedule = result is UpdateManager.CheckResult.Error
            jobFinished(params, needsReschedule)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        activeJob = null
        return true
    }
}
