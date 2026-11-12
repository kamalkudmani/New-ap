package com.revive.recovery.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revive.recovery.data.FoundImage
import com.revive.recovery.data.Restorer
import com.revive.recovery.data.RootScanner
import com.revive.recovery.data.ScanEngine
import com.revive.recovery.data.ScanEvent
import com.revive.recovery.data.ScanOptions
import com.revive.recovery.data.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ScanStatus { Idle, Running, Finished, Cancelled }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("revive", Context.MODE_PRIVATE)
    private val engine = ScanEngine(app)
    private val restorer = Restorer(app)

    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false)); private set
    var options by mutableStateOf(loadOptions()); private set
    var hasAccess by mutableStateOf(false); private set
    var rootAvailable by mutableStateOf<Boolean?>(null); private set

    // Scan state
    var status by mutableStateOf(ScanStatus.Idle); private set
    var progress by mutableStateOf(0f); private set
    var phase by mutableStateOf(""); private set
    var currentPath by mutableStateOf(""); private set
    var scannedCount by mutableStateOf(0); private set
    var elapsedMs by mutableStateOf(0L); private set
    var lastScanAt by mutableStateOf(0L); private set
    val results = mutableStateListOf<FoundImage>()
    private var scanJob: Job? = null

    // Restore state
    var restoring by mutableStateOf(false); private set
    var hud by mutableStateOf<String?>(null); private set
    val restored = mutableStateListOf<File>()

    var storageUsed by mutableStateOf(0L); private set
    var storageTotal by mutableStateOf(0L); private set

    init { refreshStorage() }

    fun completeOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun refreshAccess(context: Context) {
        hasAccess = StorageAccess.hasFullAccess(context)
        if (hasAccess) refreshRestored()
    }

    fun updateOptions(transform: (ScanOptions) -> ScanOptions) {
        options = transform(options)
        prefs.edit()
            .putBoolean("o_trash", options.trash).putBoolean("o_thumbs", options.thumbnails)
            .putBoolean("o_hidden", options.hidden).putBoolean("o_apps", options.apps)
            .putBoolean("o_carve", options.carve).putBoolean("o_root", options.root)
            .putBoolean("o_gallery", options.gallery).putInt("o_min", options.minSizeKb)
            .apply()
    }

    private fun loadOptions() = ScanOptions(
        trash = prefs.getBoolean("o_trash", true), thumbnails = prefs.getBoolean("o_thumbs", true),
        hidden = prefs.getBoolean("o_hidden", true), apps = prefs.getBoolean("o_apps", true),
        carve = prefs.getBoolean("o_carve", true), root = prefs.getBoolean("o_root", false),
        gallery = prefs.getBoolean("o_gallery", false), minSizeKb = prefs.getInt("o_min", 4),
    )

    fun setRootEnabled(enabled: Boolean) {
        if (!enabled) { updateOptions { it.copy(root = false) }; return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RootScanner.isRooted() }
            rootAvailable = ok
            updateOptions { it.copy(root = ok) }
            if (!ok) flashHud("No Root Access")
        }
    }

    fun startScan() {
        if (status == ScanStatus.Running) return
        results.clear()
        progress = 0f; scannedCount = 0; phase = "Preparing"; currentPath = ""
        status = ScanStatus.Running
        val opts = options
        scanJob = viewModelScope.launch {
            val buffer = ArrayList<FoundImage>()
            var lastFlush = 0L
            try {
                engine.scan(opts).collect { e ->
                    when (e) {
                        is ScanEvent.Progress -> {
                            progress = maxOf(progress, e.fraction); phase = e.phase
                            currentPath = e.path; scannedCount = e.scanned
                        }
                        is ScanEvent.Found -> buffer += e.image
                        is ScanEvent.Done -> { scannedCount = e.scanned; elapsedMs = e.millis }
                    }
                    val now = System.currentTimeMillis()
                    if (buffer.isNotEmpty() && (now - lastFlush > 120 || e is ScanEvent.Done)) {
                        results.addAll(buffer); buffer.clear(); lastFlush = now
                    }
                }
                results.addAll(buffer)
                sortResults()
                progress = 1f
                status = ScanStatus.Finished
                lastScanAt = System.currentTimeMillis()
            } catch (t: kotlinx.coroutines.CancellationException) {
                results.addAll(buffer)
                status = ScanStatus.Cancelled
                throw t
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        if (status == ScanStatus.Running) status = ScanStatus.Cancelled
        sortResults()
        lastScanAt = System.currentTimeMillis()
    }

    fun dismissScan() { if (status != ScanStatus.Running) status = if (results.isEmpty()) ScanStatus.Idle else ScanStatus.Finished }

    fun restore(images: List<FoundImage>, onDone: () -> Unit = {}) {
        if (images.isEmpty() || restoring) return
        restoring = true
        viewModelScope.launch {
            val n = restorer.restore(images)
            restoring = false
            refreshRestored()
            flashHud(if (n == 1) "Recovered" else "$n Recovered")
            onDone()
        }
    }

    fun refreshRestored() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { restorer.listRestored() }.getOrDefault(emptyList()) }
            restored.clear(); restored.addAll(list)
        }
    }

    /** Puts the scan UI into a given state without touching storage (used by screenshot tests). */
    @androidx.annotation.VisibleForTesting
    internal fun showcase(status: ScanStatus, progress: Float, phase: String, path: String, scanned: Int, onboarded: Boolean = true) {
        this.status = status; this.progress = progress; this.phase = phase
        this.currentPath = path; this.scannedCount = scanned; this.onboarded = onboarded
        this.lastScanAt = System.currentTimeMillis() - 120_000; this.elapsedMs = 83_000
        this.hasAccess = true
    }

    private fun sortResults() {
        val sorted = results.sortedByDescending { it.modified }
        results.clear(); results.addAll(sorted)
    }

    fun countFor(source: Source?) = if (source == null) results.size else results.count { it.source == source }

    private fun flashHud(text: String) {
        viewModelScope.launch { hud = text; delay(1400); hud = null }
    }

    private fun refreshStorage() {
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            storageTotal = st.totalBytes
            storageUsed = st.totalBytes - st.availableBytes
        }
    }
}

object StorageAccess {
    fun hasFullAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
