package com.ember.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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

            EmberTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                EmberNavHost(initialRoute = pendingNavRoute.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra("navigate_to")
        if (route != null) {
            pendingNavRoute.value = route
            intent.removeExtra("navigate_to")
        }
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
