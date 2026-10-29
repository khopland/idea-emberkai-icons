package com.github.khopland.symbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomManager

/**
 * Recognizes Spring XML configuration so it gets the Emberkai Spring icon instead of the generic XML
 * artwork. The DOM-backed implementation is registered only when the IDE has XML support; without
 * it, no detector exists and XML files keep their mapped icon.
 */
internal interface SpringXmlConfigDetector {
    /** Requires smart mode; callers check [com.intellij.openapi.project.DumbService] first. */
    fun isSpringConfig(file: VirtualFile, project: Project): Boolean

    companion object {
        private const val SPRING_DESCRIPTION_PREFIX = "com.intellij.spring."

        fun getInstance(): SpringXmlConfigDetector? =
            ApplicationManager.getApplication().getService(SpringXmlConfigDetector::class.java)

        fun isSpringDescription(className: String): Boolean = className.startsWith(SPRING_DESCRIPTION_PREFIX)
    }
}

/**
 * Spring declares its config files through DOM file descriptions; detecting that description avoids
 * a compile-time dependency on the Spring plugin.
 */
internal class DomSpringXmlConfigDetector : SpringXmlConfigDetector {
    override fun isSpringConfig(file: VirtualFile, project: Project): Boolean {
        if (!"xml".equals(file.extension, true)) return false
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return false
        val domManager = DomManager.getDomManager(project)
        return CachedValuesManager.getCachedValue(xmlFile) {
            val description = domManager.getDomFileDescription(xmlFile)
            CachedValueProvider.Result.create(
                description != null && SpringXmlConfigDetector.isSpringDescription(description.javaClass.name),
                domManager,
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }
}
