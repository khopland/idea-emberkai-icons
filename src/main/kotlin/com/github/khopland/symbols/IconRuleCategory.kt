package com.github.khopland.symbols

/** Context-aware icon families that users can enable or disable independently. */
internal enum class IconRuleCategory(val displayName: String) {
    CI("CI configuration"),
    MIGRATION("Database migrations"),
    HELM("Helm charts"),
    ANSIBLE("Ansible"),
    INFRASTRUCTURE("Infrastructure as code"),
    GITOPS("Flux and Argo CD"),
    API_SPECIFICATION("OpenAPI and Swagger"),
    KUBERNETES("Kubernetes and Kustomize"),
    SPRING_BOOT("Spring Boot"),
}
