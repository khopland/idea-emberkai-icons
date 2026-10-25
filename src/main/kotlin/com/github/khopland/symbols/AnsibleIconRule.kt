package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Identifies Ansible playbooks by structure and inventory files by their location. */
internal class AnsibleIconRule : ContextIconRule {
    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name == "ansible.cfg") return "context.ansible"
        if (isInInventory(file)) return "context.ansible"
        val extension = file.extension?.lowercase(Locale.ROOT) ?: return null
        if (extension !in PLAYBOOK_EXTENSIONS) return null
        return if (FileContentCache.inspect(file, PLAYBOOK, false) { text ->
                PLAYBOOK_HOSTS.matcher(text).find() && PLAYBOOK_TASKS.matcher(text).find()
            }) {
            "context.ansible"
        } else {
            null
        }
    }

    override fun folderIconKey(directory: VirtualFile): String? {
        val name = directory.name.lowercase(Locale.ROOT)
        if (name == "ansible") return "context.ansible-folder"
        if (name !in ANSIBLE_SUBDIRECTORIES) return null
        return if (hasAnsibleRoot(directory.parent)) "context.ansible-folder" else null
    }

    private fun isInInventory(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase(Locale.ROOT)
        if (extension != null && extension !in INVENTORY_EXTENSIONS) return false
        var parent = file.parent
        while (parent != null) {
            if (parent.name.lowercase(Locale.ROOT) in INVENTORY_DIRECTORIES) return true
            parent = parent.parent
        }
        return false
    }

    private fun hasAnsibleRoot(directory: VirtualFile?): Boolean {
        var current = directory
        while (current != null) {
            if (current.name.equals("ansible", ignoreCase = true) || current.findChild("ansible.cfg") != null) return true
            current = current.parent
        }
        return false
    }

    private companion object {
        val PLAYBOOK_EXTENSIONS = setOf("yaml", "yml")
        val INVENTORY_EXTENSIONS = setOf("ini", "yaml", "yml", "json")
        val INVENTORY_DIRECTORIES = setOf("inventory", "inventories")
        val ANSIBLE_SUBDIRECTORIES = setOf("playbooks", "roles", "inventory", "inventories")
        val PLAYBOOK_HOSTS = Pattern.compile("(?m)^\\s*(?:-\\s*)?hosts\\s*:")
        val PLAYBOOK_TASKS = Pattern.compile("(?m)^\\s*(?:tasks|roles|handlers|pre_tasks|post_tasks|import_playbook)\\s*:")
        val PLAYBOOK: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.ansiblePlaybook")
    }
}
