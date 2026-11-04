package com.galmerom.outfittracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class LeaveGate {
    var onLeave: (() -> Unit)? by mutableStateOf(null)
}

val LocalLeaveGate = staticCompositionLocalOf { LeaveGate() }
