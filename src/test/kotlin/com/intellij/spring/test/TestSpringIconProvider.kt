package com.intellij.spring.test

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import javax.swing.Icon

/** Stands in for Spring's icon provider in precedence tests. */
internal class TestSpringIconProvider(private val icon: Icon) : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? =
        if (element is KtClassOrObject) icon else null
}
