package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.ui.ElectronicsSplashScreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    private var launchedMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ElectronicsSplashScreen(Modifier.fillMaxSize())
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MILLIS)
                launchMainActivity()
            }
        }
    }

    private fun launchMainActivity() {
        if (launchedMain || isFinishing || isDestroyed) return
        launchedMain = true
        val mainIntent = Intent(this, MainActivity::class.java).putExtras(intent)
        startActivity(mainIntent)
        finish()
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_500L
    }
}
