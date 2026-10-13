package com.ember.reader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ember.reader.core.repository.AppAccent
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.navigation.EmberNavHost
import com.ember.reader.ui.theme.EmberTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    private val pendingNavRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleIntent(intent)
        setContent {
            val themeMode by appPreferencesRepository.themeModeFlow
                .collectAsState(initial = ThemeMode.SYSTEM)
            val oledBlack by appPreferencesRepository.oledBlackFlow
                .collectAsState(initial = false)
            val accent by appPreferencesRepository.accentFlow
                .collectAsState(initial = AppAccent.EMBER)

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // The system bar icons must follow the app's theme, not the device's: with the
            // device dark and the app light they would be white on a light background.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM) {
                        darkTheme
                    }
                )
                onDispose {}
            }

            EmberTheme(
                darkTheme = darkTheme,
                oledBlack = oledBlack,
                accent = accent
            ) {
                EmberNavHost(
                    initialRoute = pendingNavRoute.value,
                    // Cleared once used, so asking for the same place again works.
                    onInitialRouteHandled = { pendingNavRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        if (route != null) {
            pendingNavRoute.value = route
            intent.removeExtra(EXTRA_NAVIGATE_TO)
        }
    }

    // dispatchKeyEvent is a public method on android.app.Activity; lint flags it as
    // RestrictedApi only because androidx.activity.ComponentActivity re-declares it
    // with @RestrictedTo(LIBRARY_GROUP_PREFIX). Overriding from a downstream Activity
    // is the intended pattern for intercepting key events.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeKeyHandler?.let {
                        it(true)
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeKeyHandler?.let {
                        it(false)
                        return true
                    }
                }
            }
        }
        // Consume ACTION_UP too when handler is active to prevent volume change
        if (event.action == KeyEvent.ACTION_UP && volumeKeyHandler != null) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        /** An intent extra naming the route to open, as a notification or the widget asks. */
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        /**
         * Set by reader screens when volume page turn is enabled.
         * Callback receives true for next page, false for previous page.
         * Set to null when leaving the reader to restore normal volume behavior.
         */
        var volumeKeyHandler: ((forward: Boolean) -> Unit)? = null

        // The scrims enableEdgeToEdge() uses by default behind three-button navigation.
        private val LIGHT_NAVIGATION_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
