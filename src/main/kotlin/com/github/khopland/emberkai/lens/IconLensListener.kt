package com.github.khopland.emberkai.lens

import com.intellij.util.messages.Topic

/** Told on the EDT when a project's active lens changes, so views showing it can redraw. */
internal fun interface IconLensListener {
    fun lensChanged(lens: Lens?)

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<IconLensListener> = Topic.create("Emberkai lens changed", IconLensListener::class.java)
    }
}
