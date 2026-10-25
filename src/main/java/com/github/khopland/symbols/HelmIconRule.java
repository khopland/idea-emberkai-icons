package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Recognizes chart metadata, templates, and folders using a Chart.yaml marker. */
final class HelmIconRule implements ContextIconRule {
    private static final Pattern VALUES = Pattern.compile("values(?:-.+)?\\.ya?ml");
    private static final Set<String> CHART_DIRECTORIES = Set.of("templates", "charts", "crds");

    @Override
    public String fileIconKey(VirtualFile file, String name, Project project) {
        if (name.equals("chart.yaml")) return "context.helm";
        if (!isMetadata(name) && !isTemplate(name)) return null;

        String relative = file.getName();
        for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
            if (hasChart(dir)) {
                relative = relative.toLowerCase(Locale.ROOT);
                boolean metadata = relative.equals(name) && isMetadata(name);
                boolean template = relative.startsWith("templates/") && isTemplate(name);
                boolean crd = relative.startsWith("crds/") && isYaml(name);
                return metadata || template || crd ? "context.helm" : null;
            }
            relative = dir.getName() + "/" + relative;
            if (project != null && dir.getPath().equals(project.getBasePath())) break;
        }
        return null;
    }

    @Override
    public String folderIconKey(VirtualFile directory) {
        if (hasChart(directory)) return "context.helm-folder";
        VirtualFile parent = directory.getParent();
        return parent != null && CHART_DIRECTORIES.contains(directory.getName().toLowerCase(Locale.ROOT))
                && hasChart(parent) ? "context.helm-folder" : null;
    }

    private static boolean isMetadata(String name) {
        return name.equals("chart.lock") || name.equals("values.schema.json") || VALUES.matcher(name).matches();
    }

    private static boolean isTemplate(String name) {
        return isYaml(name) || name.endsWith(".tpl") || name.equals("notes.txt");
    }

    private static boolean isYaml(String name) {
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static boolean hasChart(VirtualFile directory) {
        VirtualFile chart = directory.findChild("Chart.yaml");
        return chart != null && !chart.isDirectory();
    }
}
