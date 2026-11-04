package com.galmerom.outfittracker.ui

import android.content.Context
import com.galmerom.outfittracker.R
import com.galmerom.outfittracker.domain.logic.errorKind
import com.galmerom.outfittracker.domain.model.ErrorKind

enum class UserMessage {
    GENERIC,
    PERMISSION,
    SIGN_IN_FAILED,
    CAMERA_PERMISSION,
    NEED_ENTITY,
    NEED_CLOTHES,
    NEED_DESCRIPTION,
    AI_FAILED,
    AI_EMPTY,
    CLOTHING_SAVED,
    MEETING_SAVED,
    SAVED_WITHOUT_PHOTO,
}

fun Throwable.toUserMessage(): UserMessage = when (errorKind()) {
    ErrorKind.PERMISSION -> UserMessage.PERMISSION
    ErrorKind.OTHER -> UserMessage.GENERIC
}

fun Context.textFor(message: UserMessage): String = when (message) {
    UserMessage.GENERIC -> getString(R.string.error_generic)
    UserMessage.PERMISSION -> getString(R.string.error_permission)
    UserMessage.SIGN_IN_FAILED -> getString(R.string.sign_in_failed)
    UserMessage.CAMERA_PERMISSION -> getString(R.string.camera_permission_denied)
    UserMessage.NEED_ENTITY -> getString(R.string.meeting_need_entity, getString(R.string.entity_label))
    UserMessage.NEED_CLOTHES -> getString(R.string.meeting_need_clothes)
    UserMessage.NEED_DESCRIPTION -> getString(R.string.clothing_description_required)
    UserMessage.AI_FAILED -> getString(R.string.ai_failed)
    UserMessage.AI_EMPTY -> getString(R.string.ai_empty)
    UserMessage.CLOTHING_SAVED -> getString(R.string.clothing_saved)
    UserMessage.MEETING_SAVED -> getString(R.string.meeting_saved)
    UserMessage.SAVED_WITHOUT_PHOTO -> getString(R.string.saved_without_photo)
}
