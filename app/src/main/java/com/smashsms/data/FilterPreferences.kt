package com.smashsms.data

import android.content.Context
import androidx.core.content.edit

object FilterPreferences {
    private const val PREFS_NAME = "smash_sms_prefs"
    private const val KEY_BLOCK_UNKNOWN_IMAGES = "block_unknown_images"

    fun isBlockUnknownImagesEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCK_UNKNOWN_IMAGES, true)
    }

    fun setBlockUnknownImagesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_BLOCK_UNKNOWN_IMAGES, enabled)
        }
    }
}
