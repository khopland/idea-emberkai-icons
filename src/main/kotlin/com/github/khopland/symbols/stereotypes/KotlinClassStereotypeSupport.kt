package com.github.khopland.symbols.stereotypes

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.LayeredIcon
import javax.swing.Icon
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Reads Kotlin annotations, supertypes, and imports from stubs; registered only when the Kotlin
 * plugin is present. Kotlin icons skip platform layer providers, so [KotlinStereotypeIconProvider]
 * draws the badge for both declarations and single-class files.
 */
internal class KotlinClassStereotypeSupport : ClassStereotypeSupport {
    override val usesIconLayers: Boolean get() = false

    override fun badgeFor(element: PsiElement): StereotypeMatch? = when (element) {
        is KtClassOrObject -> stereotypeOf(element)
        is KtFile -> fileStereotype(element)
        else -> null
    }

    override fun fileStereotype(file: PsiFile): StereotypeMatch? {
        val ktFile = file as? KtFile ?: return null
        if (ktFile.isCompiled || ktFile.isScript()) return null
        return singleClass(ktFile)?.let(::stereotypeOf)
    }

    /** Mirrors the Kotlin plugin's rule for showing a class icon on its file. */
    private fun singleClass(file: KtFile): KtClassOrObject? {
        val declarations = file.declarations.filter {
            it !is KtTypeAlias && !it.hasModifier(KtTokens.PRIVATE_KEYWORD)
        }
        val single = declarations.singleOrNull() as? KtClassOrObject ?: return null
        val name = file.virtualFile?.nameWithoutExtension ?: return null
        return single.takeIf { it.name == name }
    }

    private fun stereotypeOf(declaration: KtClassOrObject): StereotypeMatch? {
        val file = declaration.containingKtFile
        if (file.isCompiled) return null
        return CachedValuesManager.getCachedValue(declaration, CACHE) {
            CachedValueProvider.Result.create(compute(declaration, file), file)
        }
    }

    private fun compute(declaration: KtClassOrObject, file: KtFile): StereotypeMatch? {
        val annotations = declaration.annotationEntries.mapNotNull { entry ->
            entry.typeReference?.typeElement?.let(::typeName)
        }
        val supertypes = declaration.superTypeListEntries.mapNotNull { entry ->
            entry.typeReference?.typeElement?.let(::typeName)
        }
        val methodAnnotations = declaration.body?.functions.orEmpty().flatMap { function ->
            val name = function.name ?: return@flatMap emptyList()
            function.annotationEntries.mapNotNull { entry -> entry.typeReference?.typeElement?.let(::typeName)?.let { it to name } }
        }
        if (annotations.isEmpty() && supertypes.isEmpty() && methodAnnotations.isEmpty()) return null
        return ClassStereotype.match(annotations, supertypes, imports(file), methodAnnotations)
    }

    private fun typeName(type: KtTypeElement): String? {
        var userType = type as? KtUserType ?: return null
        val parts = mutableListOf(userType.referencedName ?: return null)
        while (true) {
            userType = userType.qualifier ?: break
            parts += userType.referencedName ?: return null
        }
        return parts.asReversed().joinToString(".")
    }

    private fun imports(file: KtFile): SourceImports {
        val single = mutableMapOf<String, String>()
        val onDemand = mutableSetOf<String>()
        for (directive in file.importDirectives) {
            val name = directive.importedFqName?.asString() ?: continue
            when {
                directive.isAllUnder -> onDemand += name
                else -> single[directive.aliasName ?: name.substringAfterLast('.')] = name
            }
        }
        return SourceImports(file.packageFqName.asString(), single, onDemand)
    }

    private companion object {
        val CACHE = Key.create<CachedValue<StereotypeMatch?>>("emberkai.kotlinStereotype")
    }
}

/**
 * The Kotlin plugin's icon provider returns finished icons that the platform does not pass through
 * icon layer providers. This provider runs before it, asks the providers after it for the native
 * icon exactly as the platform would, and only adds the stereotype glyph on top, so Kotlin's type
 * icon, modifier marks, and visibility column stay as they were.
 */
internal class KotlinStereotypeIconProvider : IconProvider(), DumbAware {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is KtClassOrObject && element !is KtFile) return null
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.STEREOTYPE_BADGES)) return null
        val badge = ClassStereotypes.badgeFor(element)?.stereotype?.icon ?: return null
        val native = nativeIcon(element, flags) ?: return null
        return LayeredIcon.layeredIcon(arrayOf(native, badge))
    }

    private fun nativeIcon(element: PsiElement, flags: Int): Icon? {
        val dumb = DumbService.isDumb(element.project)
        return IconProvider.EXTENSION_POINT_NAME.extensionList.asSequence()
            .dropWhile { it !== this }
            .drop(1)
            .filter { !dumb || it is DumbAware || (it as? PossiblyDumbAware)?.isDumbAware == true }
            .firstNotNullOfOrNull { it.getIcon(element, flags) }
    }
}
