package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/** Returns mapping keys so tool detection stays independent of artwork paths. */
interface ContextIconRule {
    default String fileIconKey(VirtualFile file, String name, Project project) {
        return null;
    }

    default String folderIconKey(VirtualFile directory) {
        return null;
    }
}
