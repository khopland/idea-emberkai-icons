package com.github.khopland.symbols

import com.intellij.openapi.module.Module
import java.awt.Color
import java.lang.reflect.Method
import java.util.IdentityHashMap
import javax.swing.Icon

/** Spring Modulith package roles, mapped to the lock colors used by the framework plugin. */
internal enum class ModulithLock(val color: Color) {
    PUBLIC(Color(0x276547)),
    INTERNAL(Color(0xBD3F36)),
    NESTED(Color(0xB97913)),
}

/**
 * Reads Spring Modulith's package icons defensively. The optional plugin dependency makes the
 * classes visible when Modulith is installed; if it is missing or its internals change, every
 * lookup returns null and Emberkai leaves the project view untouched.
 */
internal object SpringModulithSupport {
    private val icons: Map<Icon, ModulithLock> by lazy { loadIcons() }

    private val libraryProbe: Method? by lazy {
        runCatching {
            val type = Class.forName(
                "com.intellij.spring.modulith.SpringModulithLibraryUtilsKt",
                false,
                SpringModulithSupport::class.java.classLoader,
            )
            type.getMethod("hasModulithLibrary", Module::class.java)
        }.getOrNull()
    }

    fun lockOf(icon: Icon?): ModulithLock? = icon?.let(icons::get)

    fun hasModulithLibrary(module: Module): Boolean {
        val probe = libraryProbe ?: return false
        return runCatching { probe.invoke(null, module) as? Boolean ?: false }.getOrDefault(false)
    }

    private fun loadIcons(): Map<Icon, ModulithLock> {
        val type = runCatching {
            Class.forName(
                "com.intellij.spring.modulith.SpringModulithIcons",
                false,
                SpringModulithSupport::class.java.classLoader,
            )
        }.getOrNull() ?: return emptyMap()

        val icons = IdentityHashMap<Icon, ModulithLock>()
        for ((lock, field) in LOCK_FIELDS) {
            val icon = runCatching { type.getField(field).get(null) as? Icon }.getOrNull() ?: continue
            icons[icon] = lock
        }
        return icons
    }

    private val LOCK_FIELDS = listOf(
        ModulithLock.PUBLIC to "PackagePublic",
        ModulithLock.INTERNAL to "PackageInternal",
        ModulithLock.NESTED to "PackageNested",
    )
}
