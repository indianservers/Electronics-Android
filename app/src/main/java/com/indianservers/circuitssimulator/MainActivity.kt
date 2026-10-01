package com.indianservers.circuitssimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowInsetsControllerCompat
import com.indianservers.circuitssimulator.ui.SimulatorScreen
import com.indianservers.circuitssimulator.ui.SimulatorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.rgb(7, 21, 34)
        window.navigationBarColor = android.graphics.Color.rgb(7, 21, 34)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
        val model = ViewModelProvider(this)[SimulatorViewModel::class.java]
        setContent { SimulatorScreen(model) }
    }
}
