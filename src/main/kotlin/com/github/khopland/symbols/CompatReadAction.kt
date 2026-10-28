package com.github.khopland.symbols

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.ThrowableComputable

/**
 * Blocking read action that works from since-build 242. `ReadAction.computeBlocking` only exists
 * from 2026.1, where `ReadAction.compute` is deprecated but still available.
 */
@Suppress("DEPRECATION")
internal fun <T> computeReadAction(action: () -> T): T =
    ReadAction.compute(ThrowableComputable<T, RuntimeException> { action() })
