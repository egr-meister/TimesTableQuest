package com.timestablequest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestablequest.app.data.local.AppSettings
import com.timestablequest.app.ui.AppNavHost
import com.timestablequest.app.ui.theme.LocalReducedMotion
import com.timestablequest.app.ui.theme.TimesTableQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Matching launch screen, dismissed as soon as the first frame is ready (no artificial delay).
        installSplashScreen()
        // Edge-to-edge (enforced for targetSdk 35+); system bars stay visible, no immersive mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as TimesTableQuestApplication).container
        setContent {
            val settings by container.preferences.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CompositionLocalProvider(LocalReducedMotion provides settings.reducedMotion) {
                TimesTableQuestTheme {
                    // Horizontal display-cutout insets are applied once here; screens handle system bars.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                    ) {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
