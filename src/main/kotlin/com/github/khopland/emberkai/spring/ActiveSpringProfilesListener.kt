package com.github.khopland.emberkai.spring

import com.intellij.util.messages.Topic

/** Told on the EDT after the selected run configuration's Spring profiles change. */
internal fun interface ActiveSpringProfilesListener {
    fun profilesChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<ActiveSpringProfilesListener> =
            Topic.create("Emberkai active Spring profiles changed", ActiveSpringProfilesListener::class.java)
    }
}
