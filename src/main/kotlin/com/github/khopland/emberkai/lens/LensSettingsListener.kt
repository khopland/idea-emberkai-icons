package com.github.khopland.emberkai.lens

import com.intellij.util.messages.Topic

/** Told on the EDT after the Lenses settings page is applied with changes. */
internal fun interface LensSettingsListener {
    fun lensSettingsChanged()

    companion object {
        @Topic.AppLevel
        val TOPIC: Topic<LensSettingsListener> = Topic.create("Emberkai lens settings changed", LensSettingsListener::class.java)
    }
}
