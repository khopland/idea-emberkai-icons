package com.github.khopland.symbols.stereotypes

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/** Reads Java class annotations and imports from stubs; registered only when the Java plugin is present. */
internal class JavaClassStereotypeSupport : ClassStereotypeSupport {
    // JavaFileIconPatcher draws a file with the badged class icon, so only classes get the layer.
    override fun badgeFor(element: PsiElement): StereotypeMatch? =
        (element as? PsiClass)?.let(::stereotypeOf)

    override fun fileStereotype(file: PsiFile): StereotypeMatch? {
        val javaFile = file as? PsiJavaFile ?: return null
        if (javaFile is PsiCompiledElement) return null
        val name = file.virtualFile?.nameWithoutExtension ?: return null
        return javaFile.classes.singleOrNull { it.name == name }?.let(::stereotypeOf)
    }

    private fun stereotypeOf(psiClass: PsiClass): StereotypeMatch? {
        // Kotlin light classes and compiled classes would need analysis or decompilation.
        if (psiClass is PsiCompiledElement || psiClass is PsiTypeParameter) return null
        // The badge shares the top-left corner with Java's final mark; the native mark wins.
        if (psiClass.hasModifierProperty(PsiModifier.FINAL)) return null
        val file = psiClass.containingFile as? PsiJavaFile ?: return null
        if (file is PsiCompiledElement) return null
        return CachedValuesManager.getCachedValue(psiClass, CACHE) {
            CachedValueProvider.Result.create(compute(psiClass, file), file)
        }
    }

    private fun compute(psiClass: PsiClass, file: PsiJavaFile): StereotypeMatch? {
        val annotations = psiClass.modifierList?.annotations.orEmpty()
            .mapNotNull { it.nameReferenceElement?.text }
        // Reference elements would load the file's AST; stub types keep the names as written.
        val supertypes = (psiClass.extendsListTypes + psiClass.implementsListTypes)
            .map { (it as? PsiClassReferenceType)?.reference?.text ?: it.className }
            .map { it.substringBefore('<') }
        val methodAnnotations = psiClass.methods.flatMap { method ->
            method.modifierList.annotations.mapNotNull { annotation ->
                annotation.nameReferenceElement?.text?.let { it to method.name }
            }
        }
        if (annotations.isEmpty() && supertypes.isEmpty() && methodAnnotations.isEmpty()) return null
        return ClassStereotype.match(annotations, supertypes, imports(file), methodAnnotations)
    }

    private fun imports(file: PsiJavaFile): SourceImports {
        val single = mutableMapOf<String, String>()
        val onDemand = mutableSetOf<String>()
        for (statement in file.importList?.importStatements.orEmpty()) {
            val name = statement.qualifiedName ?: continue
            if (statement.isOnDemand) onDemand += name else single[name.substringAfterLast('.')] = name
        }
        return SourceImports(file.packageName, single, onDemand)
    }

    private companion object {
        val CACHE = Key.create<CachedValue<StereotypeMatch?>>("emberkai.javaStereotype")
    }
}
