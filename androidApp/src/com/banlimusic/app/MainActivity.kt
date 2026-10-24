package com.banlimusic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.banlimusic.app.core.datastore.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.feature.agreement.AgreementScreen
import com.banlimusic.app.ui.theme.BanLiMusicTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private var contentFrameRateHz: Float = 0f
    private var widgetRequested by mutableStateOf(false)
    private var widgetOpenPlayer by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        requestNotificationPermissionIfNeeded()
        val container = (application as MusicApplication).container

        setContent {
            val settingsState by container.settingsRepository.settings
                .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val settings = settingsState
            val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LaunchedEffect(settings?.highRefreshRateLists ?: true) {
                applyListRefreshRate(settings?.highRefreshRateLists ?: true)
            }
            BanLiMusicTheme(
                themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = settings?.dynamicColor ?: true,
            ) {
                val scope = rememberCoroutineScope()
                when {
                    settings == null -> Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    !settings.agreementAccepted -> AgreementScreen(
                        onAccept = {
                            scope.launch {
                                container.settingsRepository.setAgreementAccepted(true)
                            }
                        },
                    )

                    else -> {
                        LaunchedEffect(settings.autoImportLocalMusic) {
                            if (settings.autoImportLocalMusic && hasAudioPermission()) {
                                container.localMusicRepository.importAll()
                            }
                        }
                        AppRoot(
                            container = container,
                            initialSettings = settings,
                            onExitApp = { finish() },
                            widgetRequested = widgetRequested,
                            widgetOpenPlayer = widgetOpenPlayer,
                            onWidgetRequestHandled = { widgetRequested = false },
                        )
                    }
                }
            }
        }
    }

    /**
     * Track lists look noticeably smoother on 90Hz panels. The preference is
     * applied to the whole window (a per-screen rate would flicker on navigation)
     * and falls back to the system mode when the display cannot report 90Hz.
     */
    @Suppress("DEPRECATION")
    private fun applyListRefreshRate(enabled: Boolean) {
        val attributes = window.attributes
        if (!enabled) {
            attributes.preferredRefreshRate = 0f
            attributes.preferredDisplayModeId = 0
            window.attributes = attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { window.setFrameRateBoostOnTouchEnabled(false) }
            }
            requestContentFrameRate(0f)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Lets the platform raise the refresh rate while the list is touched
            // instead of pinning the panel to the power-saving 60Hz mode.
            runCatching { window.setFrameRateBoostOnTouchEnabled(true) }
        }
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
        val target = currentDisplay?.supportedModes
            ?.filter { it.refreshRate in MIN_HIGH_REFRESH_HZ..MAX_HIGH_REFRESH_HZ }
            ?.minByOrNull { abs(it.refreshRate - TARGET_REFRESH_HZ) }
        if (target != null) {
            attributes.preferredDisplayModeId = target.modeId
            attributes.preferredRefreshRate = target.refreshRate
        } else {
            attributes.preferredRefreshRate = TARGET_REFRESH_HZ
        }
        window.attributes = attributes
        requestContentFrameRate(target?.refreshRate ?: TARGET_REFRESH_HZ)
    }

    /**
     * Android 15 ignores [android.view.WindowManager.LayoutParams.preferredRefreshRate]
     * for apps targeting API 35+, so the modern per-view request is used as well.
     * The render node only exists after the first draw, so the request is deferred
     * to the first frame and re-applied when the activity comes back to the front.
     */
    private fun requestContentFrameRate(hz: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        contentFrameRateHz = hz
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val applyAll = {
            fun apply(view: android.view.View) {
                view.setRequestedFrameRate(hz)
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) apply(view.getChildAt(index))
                }
            }
            apply(window.decorView)
        }
        if (content.isAttachedToWindow && content.width > 0) {
            applyAll()
        } else {
            content.viewTreeObserver.addOnDrawListener(
                object : android.view.ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        content.viewTreeObserver.removeOnDrawListener(this)
                        applyAll()
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (contentFrameRateHz > 0f) requestContentFrameRate(contentFrameRateHz)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FROM_WIDGET, false) == true) {
            widgetOpenPlayer = intent.getBooleanExtra(EXTRA_WIDGET_OPEN_PLAYER, false)
            widgetRequested = true
            // Consume the extras so a configuration change does not re-open the player.
            intent.removeExtra(EXTRA_FROM_WIDGET)
            intent.removeExtra(EXTRA_WIDGET_OPEN_PLAYER)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TARGET_REFRESH_HZ = 90f
        const val MIN_HIGH_REFRESH_HZ = 80f
        const val MAX_HIGH_REFRESH_HZ = 100f
        const val EXTRA_FROM_WIDGET = "com.banlimusic.app.extra.FROM_WIDGET"
        const val EXTRA_WIDGET_OPEN_PLAYER = "com.banlimusic.app.extra.WIDGET_OPEN_PLAYER"
    }
}
