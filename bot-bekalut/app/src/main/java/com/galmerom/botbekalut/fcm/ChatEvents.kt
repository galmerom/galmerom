package com.galmerom.botbekalut.fcm

import kotlinx.coroutines.flow.MutableSharedFlow

object ChatEvents {
    val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tokens = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
