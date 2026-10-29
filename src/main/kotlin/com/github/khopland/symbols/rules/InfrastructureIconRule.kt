package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Recognizes AWS SAM and CloudFormation files by filename or AWS template markers. */
internal class InfrastructureIconRule : ContextIconRule {
    override val category = IconRuleCategory.INFRASTRUCTURE

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name == "azure.yaml") return "context.infrastructure"
        if (name == "samconfig.toml" || name == "cdk.json" || name == "cdk.context.json") {
            return "context.infrastructure"
        }

        val extension = file.extension?.lowercase(Locale.ROOT) ?: return null
        if (extension !in TEMPLATE_EXTENSIONS) return null
        return if (isAwsTemplate(file)) {
            "context.infrastructure"
        } else {
            null
        }
    }

    override fun recognizesFile(file: VirtualFile, name: String, project: Project?): Boolean =
        name in TERRAFORM_NAMES || TERRAFORM_EXTENSIONS.any { name.endsWith(it) } ||
            fileIconKey(file, name, project) != null

    private fun isAwsTemplate(file: VirtualFile): Boolean = FileContentCache.inspect(
        file, AWS_TEMPLATE, false,
    ) { text -> AWS_MARKER.matcher(text).find() }

    private companion object {
        val TEMPLATE_EXTENSIONS = setOf("yaml", "yml", "json")
        val TERRAFORM_NAMES = setOf(".terraform-version", ".terraform.lock.hcl", "terraform.rc", "terragrunt.hcl")
        val TERRAFORM_EXTENSIONS = listOf(".tf", ".tf.json", ".tfvars", ".tofu")
        val AWS_MARKER = Pattern.compile("\\b(?:AWSTemplateFormatVersion|AWS::[A-Za-z0-9]+::[A-Za-z0-9]+)\\b")
        val AWS_TEMPLATE: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.awsTemplate")
    }
}
