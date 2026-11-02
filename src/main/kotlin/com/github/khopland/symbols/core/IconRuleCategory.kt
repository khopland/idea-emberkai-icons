package com.github.khopland.symbols.core

/** Where a category's toggle sits on the settings page, by what turning it off hides. */
internal enum class CategoryGroup(val title: String, val hint: String) {
    TOOL_ICONS("Tool icons", "Artwork for recognized tool files. Turning a family off keeps standard file icons."),
    WARNINGS("Badges and warnings", "Badges and banners that flag something to check before it ships."),
    MARKERS("Markers and hints", "Badges that describe a file's role, environment, or live state."),
}

/** Context-aware icon families that users can enable or disable independently. */
internal enum class IconRuleCategory(val displayName: String, val group: CategoryGroup) {
    CI("CI configuration", CategoryGroup.TOOL_ICONS),
    MIGRATION("Database migrations", CategoryGroup.TOOL_ICONS),
    HELM("Helm charts", CategoryGroup.TOOL_ICONS),
    ANSIBLE("Ansible", CategoryGroup.TOOL_ICONS),
    INFRASTRUCTURE("Infrastructure as code", CategoryGroup.TOOL_ICONS),
    GITOPS("Flux and Argo CD", CategoryGroup.TOOL_ICONS),
    API_SPECIFICATION("API contracts (OpenAPI, AsyncAPI, Avro)", CategoryGroup.TOOL_ICONS),
    KUBERNETES("Kubernetes and Kustomize", CategoryGroup.TOOL_ICONS),
    SOPS("SOPS-encrypted files", CategoryGroup.TOOL_ICONS),
    SPRING_BOOT("Spring Boot", CategoryGroup.TOOL_ICONS),
    SPRING_MODULITH("Spring Modulith package locks", CategoryGroup.MARKERS),
    JVM_FRAMEWORKS("Quarkus, Micronaut, and Ktor", CategoryGroup.TOOL_ICONS),
    ARCHITECTURE("JVM architecture folders", CategoryGroup.TOOL_ICONS),
    ENVIRONMENT_VARIANTS("Environment variant badges", CategoryGroup.MARKERS),
    UNIGNORED_SECRETS("Unignored secret warning badges", CategoryGroup.WARNINGS),
    ACTIVE_SPRING_PROFILES("Active Spring profile markers", CategoryGroup.MARKERS),
    STEREOTYPE_BADGES("Class stereotype badges", CategoryGroup.MARKERS),
    MIGRATION_CONFLICTS("Migration version conflict badges", CategoryGroup.WARNINGS),
    RUNNING_SERVICES("Running-service markers", CategoryGroup.MARKERS),
    LOCKFILE_DRIFT("Lockfile drift badges", CategoryGroup.WARNINGS),
    GENERATED_FILES("Generated file markers", CategoryGroup.MARKERS),
    PRODUCTION_EDITOR_WARNINGS("Production editor warnings", CategoryGroup.WARNINGS),
    UNPINNED_REFERENCES("Unpinned image and action badges", CategoryGroup.WARNINGS),
    CONFIG_DRIFT("Config key drift badges", CategoryGroup.WARNINGS),
}
