package io.github.andy_walker_idfa.smarthome_dashboard.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The black night screen that covers the dashboard. Touches never reach it: the activity consumes the first touch
 * to wake the screen.
 */
@Composable
fun NightOverlay() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
}
