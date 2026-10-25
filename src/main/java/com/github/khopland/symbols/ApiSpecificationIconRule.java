package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Set;

/** Handles API contracts with arbitrary filenames when their root metadata identifies them. */
final class ApiSpecificationIconRule implements ContextIconRule {
    private static final Set<String> SPEC_NAMES = Set.of("openapi.yaml", "openapi.yml", "openapi.json",
            "swagger.yaml", "swagger.yml", "swagger.json");

    @Override
    public String fileIconKey(VirtualFile file, String name, Project project) {
        return SPEC_NAMES.contains(name) || StructuredFileKind.of(file) == StructuredFileKind.OPEN_API
                ? "context.openapi" : null;
    }

    @Override
    public String folderIconKey(VirtualFile directory) {
        for (String name : SPEC_NAMES) {
            VirtualFile child = directory.findChild(name);
            if (child != null && !child.isDirectory()) return "context.openapi-folder";
        }
        return null;
    }
}
