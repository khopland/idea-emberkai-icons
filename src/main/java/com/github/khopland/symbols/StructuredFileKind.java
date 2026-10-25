package com.github.khopland.symbols;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads only top-level scalar metadata from YAML/JSON; never constructs tagged objects. */
enum StructuredFileKind {
    NONE, OPEN_API, KUBERNETES, KUSTOMIZE;

    private static final Key<FileContentCache.Inspection<StructuredFileKind>> CACHE = Key.create("emberkai.structuredKind");
    private static final Set<String> EXTENSIONS = Set.of("yaml", "yml", "json");
    private static final Pattern OPEN_API_VERSION = Pattern.compile("3\\.\\d+(?:\\.\\d+)?(?:[-+][A-Za-z0-9.-]+)?");
    private static final Pattern API_VERSION = Pattern.compile("(?:[A-Za-z0-9][A-Za-z0-9.-]*/)?v\\d+(?:(?:alpha|beta)\\d+)?");
    private static final Pattern KIND = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    static StructuredFileKind of(VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null || !EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) return NONE;
        return FileContentCache.inspect(file, CACHE, NONE, StructuredFileKind::parse);
    }

    private static StructuredFileKind parse(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(FileContentCache.MAX_FILE_SIZE);
        options.setNestingDepthLimit(32);
        options.setMaxAliasesForCollections(25);
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try {
            int documents = 0;
            for (Node document : yaml.composeAll(new StringReader(text))) {
                if (++documents > 32) break;
                if (!(document instanceof MappingNode mapping) || !mapping.getTag().equals(Tag.MAP)) continue;
                Map<String, String> fields = new HashMap<>();
                Set<String> keys = new HashSet<>();
                for (var tuple : mapping.getValue()) {
                    if (!(tuple.getKeyNode() instanceof ScalarNode key)) continue;
                    if (!keys.add(key.getValue())) return NONE;
                    if (tuple.getValueNode() instanceof ScalarNode value) fields.put(key.getValue(), value.getValue());
                }
                if (OPEN_API_VERSION.matcher(fields.getOrDefault("openapi", "")).matches()
                        || fields.getOrDefault("swagger", "").equals("2.0")) return OPEN_API;
                if (API_VERSION.matcher(fields.getOrDefault("apiVersion", "")).matches()
                        && KIND.matcher(fields.getOrDefault("kind", "")).matches()) {
                    return fields.get("kind").equals("Kustomization")
                            && fields.get("apiVersion").startsWith("kustomize.config.k8s.io/") ? KUSTOMIZE : KUBERNETES;
                }
            }
        } catch (YAMLException ignored) {
            // Incomplete or unsupported documents retain their format icon.
        }
        return NONE;
    }
}
