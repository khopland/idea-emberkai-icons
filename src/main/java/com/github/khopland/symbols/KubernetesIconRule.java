package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.Set;

/** Recognizes Kubernetes objects from root metadata and Kustomize trees from their marker file. */
final class KubernetesIconRule implements ContextIconRule {
    private static final Set<String> LAYERS = Set.of("base", "bases", "overlays", "components");
    private static final String[] MARKERS = {"kustomization.yaml", "kustomization.yml", "Kustomization"};

    @Override
    public String fileIconKey(VirtualFile file, String name, Project project) {
        if (name.equals("kustomization.yaml") || name.equals("kustomization.yml") || name.equals("kustomization")) {
            return "context.kustomize";
        }
        return switch (StructuredFileKind.of(file)) {
            case KUBERNETES -> "context.kubernetes";
            case KUSTOMIZE -> "context.kustomize";
            default -> null;
        };
    }

    @Override
    public String folderIconKey(VirtualFile directory) {
        if (hasKustomization(directory)) return "context.kustomize-folder";
        if (!LAYERS.contains(directory.getName().toLowerCase(Locale.ROOT))) return null;
        for (VirtualFile parent = directory.getParent(); parent != null; parent = parent.getParent()) {
            if (hasKustomization(parent)) return "context.kustomize-folder";
        }
        return null;
    }

    private static boolean hasKustomization(VirtualFile directory) {
        for (String name : MARKERS) {
            VirtualFile marker = directory.findChild(name);
            if (marker != null && !marker.isDirectory()) return true;
        }
        return false;
    }
}
