package com.revive.recovery

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.Coil
import coil.ImageLoader
import com.android.resources.NightMode
import com.revive.recovery.data.FoundImage
import com.revive.recovery.data.Source
import com.revive.recovery.ui.AppViewModel
import com.revive.recovery.ui.Navigator
import com.revive.recovery.ui.Route
import com.revive.recovery.ui.ScanStatus
import com.revive.recovery.ui.screens.MainTabs
import com.revive.recovery.ui.screens.OnboardingScreen
import com.revive.recovery.ui.screens.ResultsScreen
import com.revive.recovery.ui.screens.ScanningScreen
import com.revive.recovery.ui.screens.SettingsScreen
import com.revive.recovery.ui.screens.ViewerScreen
import com.revive.recovery.ui.theme.IOSTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Renders the real screens with layoutlib (no device/emulator).
 * Record:  ./gradlew recordPaparazziDebug   → app/src/test/snapshots/images
 */
abstract class ScreenshotBase(night: Boolean) {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 1.0,
    )
    @get:Rule val tmp = TemporaryFolder()

    private class TestApp(base: Context) : Application() { init { attachBaseContext(base) } }

    protected fun vm(): AppViewModel {
        Coil.setImageLoader(
            ImageLoader.Builder(paparazzi.context).dispatcher(Dispatchers.Unconfined).diskCache(null).crossfade(false).build()
        )
        return AppViewModel(TestApp(paparazzi.context))
    }

    protected fun samplePhotos(): List<FoundImage> {
        val dir = tmp.newFolder()
        val palettes = listOf(
            0xFFFF9A8B to 0xFFFF6A88, 0xFF4FACFE to 0xFF00F2FE, 0xFF43E97B to 0xFF38F9D7, 0xFFFA709A to 0xFFFEE140,
            0xFF667EEA to 0xFF764BA2, 0xFFF6D365 to 0xFFFDA085, 0xFF30CFD0 to 0xFF330867, 0xFFA8EDEA to 0xFFFED6E3,
            0xFFFF0844 to 0xFFFFB199, 0xFF0BA360 to 0xFF3CBA92, 0xFF5EE7DF to 0xFFB490CA, 0xFFD299C2 to 0xFFFEF9D7,
        )
        val sources = listOf(Source.TRASH, Source.THUMBNAIL, Source.HIDDEN, Source.APP, Source.CARVED, Source.THUMBNAIL)
        val rnd = Random(7)
        return (0 until 30).map { i ->
            val (a, b) = palettes[(i * 5) % palettes.size]
            val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
            c.drawCircle(80f + rnd.nextInt(140), 90f + rnd.nextInt(60), 30f + rnd.nextInt(40), p)
            p.color = 0x40000000
            c.drawRect(0f, 200f + rnd.nextInt(40), 300f, 300f, p)
            val f = File(dir, "p$i.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            FoundImage(
                "p$i", f, "/storage/emulated/0/DCIM/.thumbnails/p$i.jpg", sources[i % sources.size],
                180_000L + i * 7_919L, 1_762_000_000_000L - i * 86_400_000L, "image/jpeg", 300, 300,
            )
        }
    }

    private val resultOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    protected fun shot(name: String, millis: Long = 1_500, content: @Composable () -> Unit) {
        val view = ComposeView(paparazzi.context).apply {
            setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides resultOwner, LocalInspectionMode provides true) { IOSTheme { content() } } }
        }
        paparazzi.snapshot(view, name, offsetMillis = millis)
    }
}

class LightScreens : ScreenshotBase(night = false) {
    @Test fun onboarding() { val vm = vm(); shot("onboarding", 2_000) { OnboardingScreen(vm) } }

    @Test fun home() {
        val vm = vm(); vm.results.addAll(samplePhotos()); vm.showcase(ScanStatus.Finished, 1f, "", "", 48_213)
        shot("home") { MainTabs(vm, Navigator()) {} }
    }

    @Test fun scanning() {
        val vm = vm(); vm.results.addAll(samplePhotos().take(17))
        vm.showcase(ScanStatus.Running, 0.62f, "Scanning storage", "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses", 21_904)
        shot("scanning") { ScanningScreen(vm, {}, {}) }
    }

    @Test fun scanComplete() {
        val vm = vm(); vm.results.addAll(samplePhotos()); vm.showcase(ScanStatus.Finished, 1f, "", "", 48_213)
        shot("scan_complete") { ScanningScreen(vm, {}, {}) }
    }

    @Test fun results() {
        val vm = vm(); vm.results.addAll(samplePhotos()); vm.showcase(ScanStatus.Finished, 1f, "", "", 48_213)
        shot("results") { ResultsScreen(vm, Navigator()) }
    }

    @Test fun viewer() {
        val vm = vm(); val photos = samplePhotos()
        shot("viewer") { ViewerScreen(vm, Navigator(), Route.Viewer(photos, 4, recoverable = true)) }
    }

    @Test fun settings() {
        val vm = vm(); vm.showcase(ScanStatus.Idle, 0f, "", "", 0)
        shot("settings") { SettingsScreen(vm, Navigator()) }
    }
}

class DarkScreens : ScreenshotBase(night = true) {
    @Test fun home() {
        val vm = vm(); vm.results.addAll(samplePhotos()); vm.showcase(ScanStatus.Finished, 1f, "", "", 48_213)
        shot("home_dark") { MainTabs(vm, Navigator()) {} }
    }

    @Test fun results() {
        val vm = vm(); vm.results.addAll(samplePhotos()); vm.showcase(ScanStatus.Finished, 1f, "", "", 48_213)
        shot("results_dark") { ResultsScreen(vm, Navigator()) }
    }
}
