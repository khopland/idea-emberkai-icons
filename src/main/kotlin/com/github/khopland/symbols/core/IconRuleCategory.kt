package com.github.khopland.symbols.core

/** Context-aware icon families that users can enable or disable independently. */
internal enum class IconRuleCategory(val displayName: String) {
    CI("CI configuration"),
    MIGRATION("Database migrations"),
    HELM("Helm charts"),
    ANSIBLE("Ansible"),
    INFRASTRUCTURE("Infrastructure as code"),
    GITOPS("Flux and Argo CD"),
    API_SPECIFICATION("API contracts (OpenAPI, AsyncAPI, Avro)"),
    KUBERNETES("Kubernetes and Kustomize"),
    SPRING_BOOT("Spring Boot"),
    SPRING_MODULITH("Spring Modulith package locks"),
    JVM_FRAMEWORKS("Quarkus, Micronaut, and Ktor"),
    ARCHITECTURE("JVM architecture folders"),
    ENVIRONMENT_VARIANTS("Environment variant badges"),
    UNIGNORED_SECRETS("Unignored secret warning badges"),
    ACTIVE_SPRING_PROFILES("Active Spring profile markers"),
    STEREOTYPE_BADGES("Class stereotype badges"),
    MIGRATION_CONFLICTS("Migration version conflict badges"),
    RUNNING_SERVICES("Running-service markers"),
}
