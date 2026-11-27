package com.bubble.screentools.update

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.bubble.screentools.BuildConfig
import com.bubble.screentools.data.BubblePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * GitHub Releases Automatic Update Checker for `AprionoN-Officials/Bubble-Android`.
 * - Uses official GitHub Releases API without embedded tokens/credentials.
 * - Supports ETag caching (`If-None-Match` -> 304), rate-limit cooldowns, Wi-Fi Only checks,
 *   Semantic Versioning, and ABI asset metadata lookup.
 * - Opens official GitHub Releases in the user's browser for transparent download (never requests
 *   `REQUEST_INSTALL_PACKAGES` or installs APKs from inside the app).
 * - Schedules lightweight background checks via Android `JobScheduler` (never a foreground service).
 */
class UpdateManager(
    private val context: Context,
    private val preferences: BubblePreferences = BubblePreferences(context)
) {

    sealed class CheckResult {
        data class UpdateAvailable(
            val release: GitHubRelease,
            val matchingApk: ReleaseAsset?,
            val expectedSha256: String?
        ) : CheckResult()

        data class UpToDate(val currentVersion: String, val latestTag: String) : CheckResult()
        data class RateLimited(val retryAfterEpochMs: Long) : CheckResult()
        data class Skipped(val reason: String) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    fun isNetworkAllowed(manualCheck: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!manualCheck && preferences.updateWifiOnly) {
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return true
    }

    suspend fun checkForUpdates(
        manual: Boolean = false,
        installedVersionName: String = BuildConfig.VERSION_NAME
    ): CheckResult = withContext(Dispatchers.IO) {
        if (!isNetworkAllowed(manualCheck = manual)) {
            return@withContext CheckResult.Skipped(
                if (preferences.updateWifiOnly && !manual) "Waiting for Wi-Fi connection."
                else "No active internet connection."
            )
        }

        val now = System.currentTimeMillis()
        if (!manual && preferences.lastRateLimitResetMs > now) {
            return@withContext CheckResult.RateLimited(preferences.lastRateLimitResetMs)
        }

        val includePre = preferences.includePrereleaseUpdates
        val endpoint = if (includePre) RELEASES_LIST_API else LATEST_RELEASE_API

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Bubble-Android/$installedVersionName")
                val etag = preferences.lastUpdateEtag
                if (etag.isNotBlank() && !includePre && !manual) {
                    setRequestProperty("If-None-Match", etag)
                }
            }

            val status = conn.responseCode
            val remaining = conn.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull()
            val resetSec = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()

            if (status == 403 || status == 429 || remaining == 0) {
                val resetMs = if (resetSec != null) resetSec * 1000L else (now + 30 * 60 * 1000L)
                preferences.lastRateLimitResetMs = resetMs
                return@withContext CheckResult.RateLimited(resetMs)
            }

            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                preferences.lastUpdateCheckTimestampMs = now
                val cachedLatest = preferences.lastKnownLatestVersion.ifBlank { installedVersionName }
                return@withContext CheckResult.UpToDate(installedVersionName, cachedLatest)
            }

            if (status != HttpURLConnection.HTTP_OK) {
                return@withContext CheckResult.Error("GitHub API returned HTTP $status.")
            }

            conn.getHeaderField("ETag")?.takeIf { it.isNotBlank() && !includePre }?.let {
                preferences.lastUpdateEtag = it
            }

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            preferences.lastUpdateCheckTimestampMs = now

            val release: GitHubRelease? = if (includePre) {
                GitHubReleaseParser.parseReleaseList(body)
                    .filter { !it.isDraft }
                    .maxByOrNull { it.version }
            } else {
                GitHubReleaseParser.parseSingleRelease(body)
            }

            if (release == null) {
                return@withContext CheckResult.Error("Invalid or empty release metadata from GitHub.")
            }

            preferences.lastKnownLatestVersion = release.tagName
            preferences.lastKnownReleaseNotes = release.changelog
            preferences.lastKnownReleaseUrl = release.htmlUrl
            preferences.lastKnownReleaseDate = release.publishedAt

            val hasUpdate = SemVer.isUpdateAvailable(
                installedVersion = installedVersionName,
                candidateTag = release.tagName,
                candidateIsPrereleaseFlag = release.isPrerelease,
                includePrerelease = includePre
            )

            if (!hasUpdate) {
                return@withContext CheckResult.UpToDate(installedVersionName, release.tagName)
            }

            val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
            val bestApk = UpdateAssetSelector.selectBestApk(release.assets, abis)
            val expectedSha = bestApk?.let { release.expectedChecksums[it.name.lowercase()] }

            CheckResult.UpdateAvailable(
                release = release,
                matchingApk = bestApk,
                expectedSha256 = expectedSha
            )
        } catch (_: UnknownHostException) {
            CheckResult.Error("Offline: unable to reach GitHub Releases.")
        } catch (_: SocketTimeoutException) {
            CheckResult.Error("Connection timed out while checking for updates.")
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            CheckResult.Error(t.message ?: "Failed to check for updates.")
        } finally {
            conn?.disconnect()
        }
    }

    fun syncBackgroundSchedule() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (!preferences.autoUpdateCheckEnabled) {
            scheduler.cancel(JOB_ID)
            return
        }
        val intervalMs = (preferences.updateCheckInterval.hours * 3600_000L).coerceAtLeast(15 * 60_000L)
        val networkType = if (preferences.updateWifiOnly) {
            JobInfo.NETWORK_TYPE_UNMETERED
        } else {
            JobInfo.NETWORK_TYPE_ANY
        }
        val component = ComponentName(context, UpdateCheckJobService::class.java)
        val job = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(networkType)
            .setPeriodic(intervalMs)
            .setBackoffCriteria(30 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(false)
            .build()
        runCatching { scheduler.schedule(job) }
    }

    fun shouldRunLaunchCheck(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!preferences.autoUpdateCheckEnabled || !preferences.checkUpdateOnLaunch) return false
        val intervalMs = preferences.updateCheckInterval.hours * 3600_000L
        return (nowMs - preferences.lastUpdateCheckTimestampMs) >= intervalMs
    }

    companion object {
        const val REPO_URL = "https://github.com/AprionoN-Officials/Bubble-Android"
        const val RELEASES_WEB_URL = "https://github.com/AprionoN-Officials/Bubble-Android/releases"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/AprionoN-Officials/Bubble-Android/releases/latest"
        const val RELEASES_LIST_API = "https://api.github.com/repos/AprionoN-Officials/Bubble-Android/releases?per_page=10"
        private const val JOB_ID = 4109
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
