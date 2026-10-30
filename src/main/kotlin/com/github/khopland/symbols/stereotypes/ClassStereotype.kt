package com.github.khopland.symbols.stereotypes

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.NativeIconPolicy
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.ide.IconLayerProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import javax.swing.Icon

/**
 * Spring, Quarkus, Micronaut, and JPA roles shown as a small corner glyph on native class icons, in
 * priority order. Entry points, where work enters a service from outside it, outrank the roles a
 * class plays inside it, so a service with a scheduled method shows the hourglass.
 */
internal enum class ClassStereotype(val displayName: String, icon: String, val isEntryPoint: Boolean = false) {
    APPLICATION("Spring Boot application", "application", isEntryPoint = true),
    WEB("Web controller", "web", isEntryPoint = true),
    LISTENER("Message listener", "listener", isEntryPoint = true),
    SCHEDULED("Scheduled job", "scheduled", isEntryPoint = true),
    CLIENT("HTTP client", "client"),
    REPOSITORY("Repository", "repository"),
    SERVICE("Service", "service"),
    CONFIGURATION("Configuration", "configuration"),
    ENTITY("Persistent entity", "entity");

    val icon: Icon = IconLoader.getIcon("/emberkai/icons/stereotypes/$icon.svg", ClassStereotype::class.java)

    companion object {
        private val ANNOTATIONS = mapOf(
            "org.springframework.boot.autoconfigure.SpringBootApplication" to APPLICATION,
            "org.springframework.web.bind.annotation.RestController" to WEB,
            "org.springframework.stereotype.Controller" to WEB,
            "jakarta.ws.rs.Path" to WEB,
            "javax.ws.rs.Path" to WEB,
            "io.micronaut.http.annotation.Controller" to WEB,
            "org.springframework.kafka.annotation.KafkaListener" to LISTENER,
            "org.springframework.amqp.rabbit.annotation.RabbitListener" to LISTENER,
            "io.micronaut.configuration.kafka.annotation.KafkaListener" to LISTENER,
            "io.micronaut.rabbitmq.annotation.RabbitListener" to LISTENER,
            "org.springframework.cloud.openfeign.FeignClient" to CLIENT,
            "org.springframework.web.service.annotation.HttpExchange" to CLIENT,
            "org.eclipse.microprofile.rest.client.inject.RegisterRestClient" to CLIENT,
            "io.micronaut.http.client.annotation.Client" to CLIENT,
            "org.springframework.stereotype.Repository" to REPOSITORY,
            "org.springframework.data.repository.RepositoryDefinition" to REPOSITORY,
            "io.micronaut.data.annotation.Repository" to REPOSITORY,
            "io.micronaut.data.jdbc.annotation.JdbcRepository" to REPOSITORY,
            "io.micronaut.data.r2dbc.annotation.R2dbcRepository" to REPOSITORY,
            "org.springframework.stereotype.Service" to SERVICE,
            "jakarta.enterprise.context.ApplicationScoped" to SERVICE,
            "org.springframework.context.annotation.Configuration" to CONFIGURATION,
            "org.springframework.boot.autoconfigure.AutoConfiguration" to CONFIGURATION,
            "org.springframework.boot.context.properties.ConfigurationProperties" to CONFIGURATION,
            "jakarta.persistence.Entity" to ENTITY,
            "javax.persistence.Entity" to ENTITY,
            "org.springframework.data.mongodb.core.mapping.Document" to ENTITY,
            "io.micronaut.data.annotation.MappedEntity" to ENTITY,
        )

        /** Annotations that mark a class through one of its own methods. */
        private val METHOD_ANNOTATIONS = mapOf(
            "org.springframework.kafka.annotation.KafkaListener" to LISTENER,
            "org.springframework.amqp.rabbit.annotation.RabbitListener" to LISTENER,
            "org.springframework.jms.annotation.JmsListener" to LISTENER,
            "io.awspring.cloud.sqs.annotation.SqsListener" to LISTENER,
            "org.eclipse.microprofile.reactive.messaging.Incoming" to LISTENER,
            "org.springframework.scheduling.annotation.Scheduled" to SCHEDULED,
            "org.springframework.scheduling.annotation.Schedules" to SCHEDULED,
            "io.quarkus.scheduler.Scheduled" to SCHEDULED,
            "io.micronaut.scheduling.annotation.Scheduled" to SCHEDULED,
            "org.springframework.web.service.annotation.HttpExchange" to CLIENT,
            "org.springframework.web.service.annotation.GetExchange" to CLIENT,
            "org.springframework.web.service.annotation.PostExchange" to CLIENT,
            "org.springframework.web.service.annotation.PutExchange" to CLIENT,
            "org.springframework.web.service.annotation.PatchExchange" to CLIENT,
            "org.springframework.web.service.annotation.DeleteExchange" to CLIENT,
        )

        /** Spring Data, Micronaut Data, and Panache base types; a repository extending one needs no annotation. */
        private val REPOSITORY_SUPERTYPES = setOf(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.ListCrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.repository.ListPagingAndSortingRepository",
            "org.springframework.data.repository.reactive.ReactiveCrudRepository",
            "org.springframework.data.repository.reactive.ReactiveSortingRepository",
            "org.springframework.data.repository.kotlin.CoroutineCrudRepository",
            "org.springframework.data.repository.kotlin.CoroutineSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository",
            "org.springframework.data.mongodb.repository.MongoRepository",
            "org.springframework.data.mongodb.repository.ReactiveMongoRepository",
            "org.springframework.data.r2dbc.repository.R2dbcRepository",
            "org.springframework.data.elasticsearch.repository.ElasticsearchRepository",
            "io.micronaut.data.repository.CrudRepository",
            "io.micronaut.data.repository.PageableRepository",
            "io.micronaut.data.repository.reactive.ReactorCrudRepository",
            "io.micronaut.data.repository.kotlin.CoroutineCrudRepository",
            "io.quarkus.hibernate.orm.panache.PanacheRepository",
            "io.quarkus.hibernate.orm.panache.PanacheRepositoryBase",
            "io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository",
            "io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase",
        )

        /**
         * Matches annotation and supertype names as written in source. Short names count only when an
         * import (or the file's own package) supplies the expected qualified name; meta-annotations are
         * not followed and nothing is resolved. [methodAnnotations] pairs an annotation with the name
         * of the class's own method that carries it.
         */
        fun match(
            annotations: List<String>,
            supertypes: List<String>,
            imports: SourceImports,
            methodAnnotations: List<Pair<String, String>> = emptyList(),
        ): StereotypeMatch? {
            val found = mutableListOf<StereotypeMatch>()
            for (name in annotations) {
                val stereotype = imports.qualify(name, ANNOTATIONS.keys)?.let(ANNOTATIONS::get) ?: continue
                found += StereotypeMatch(stereotype, "@${name.substringAfterLast('.')}")
            }
            for ((name, method) in methodAnnotations) {
                val stereotype = imports.qualify(name, METHOD_ANNOTATIONS.keys)?.let(METHOD_ANNOTATIONS::get) ?: continue
                found += StereotypeMatch(stereotype, "@${name.substringAfterLast('.')} on $method()")
            }
            for (name in supertypes) {
                imports.qualify(name, REPOSITORY_SUPERTYPES) ?: continue
                found += StereotypeMatch(REPOSITORY, "extends ${name.substringAfterLast('.')}")
            }
            return found.minByOrNull { it.stereotype.ordinal }
        }
    }
}

internal data class StereotypeMatch(val stereotype: ClassStereotype, val evidence: String)

/** Import directives as written in a source file, read from stubs without resolving references. */
internal class SourceImports(
    private val packageName: String,
    private val single: Map<String, String>,
    private val onDemand: Set<String>,
) {
    fun qualify(name: String, known: Set<String>): String? {
        val reference = name.filterNot(Char::isWhitespace)
        if (reference.isEmpty()) return null
        if ('.' in reference) {
            // A qualified name may also start with an imported alias or class.
            val head = reference.substringBefore('.')
            val aliased = single[head]?.let { "$it.${reference.substringAfter('.')}" }
            return reference.takeIf { it in known } ?: aliased?.takeIf { it in known }
        }
        single[reference]?.let { return it.takeIf { qualified -> qualified in known } }
        return (onDemand + packageName).map { "$it.$reference" }.firstOrNull { it in known }
    }
}

/** Language integrations registered from the optional Java and Kotlin plugin descriptors. */
internal interface ClassStereotypeSupport {
    /** The stereotype for a declaration or file whose native icon should carry the badge. */
    fun badgeFor(element: PsiElement): StereotypeMatch?

    /** The stereotype of the class that IntelliJ uses for a source file's icon. */
    fun fileStereotype(file: PsiFile): StereotypeMatch?

    /** False when the language's icons skip platform layer providers and are badged another way. */
    val usesIconLayers: Boolean get() = true

    companion object {
        val EP_NAME = ExtensionPointName.create<ClassStereotypeSupport>("com.github.khopland.symbols.classStereotypeSupport")
    }
}

internal object ClassStereotypes {
    private val SOURCE_EXTENSIONS = setOf("java", "kt")

    fun badgeFor(element: PsiElement): StereotypeMatch? = badgeFor(element) { true }

    /** The badge [ClassStereotypeLayerProvider] adds; other languages badge their own icons. */
    fun layerFor(element: PsiElement): StereotypeMatch? = badgeFor(element) { it.usesIconLayers }

    fun fileStereotype(file: PsiFile): StereotypeMatch? {
        if (!file.isValid) return null
        return ClassStereotypeSupport.EP_NAME.extensionList
            .firstNotNullOfOrNull { it.fileStereotype(file) }
            ?.takeIf { isProjectSource(file) }
    }

    /**
     * Whether [file]'s own class is an application, controller, listener, or scheduled job. Ignores
     * the badge setting, like the other scopes. Reads stubs; safe from any thread.
     */
    fun isEntryPoint(file: VirtualFile, project: Project): Boolean {
        if (file.extension?.lowercase() !in SOURCE_EXTENSIONS) return false
        return runReadActionBlocking {
            if (project.isDisposed || !file.isValid) return@runReadActionBlocking false
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runReadActionBlocking false
            fileStereotype(psiFile)?.stereotype?.isEntryPoint == true
        }
    }

    /** Tooltip text for an element whose icon carries a badge; null when badges are off. */
    fun describe(element: PsiElement): String? {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.STEREOTYPE_BADGES)) return null
        return badgeFor(element)?.let { "${it.stereotype.displayName} stereotype (${it.evidence})" }
    }

    private inline fun badgeFor(
        element: PsiElement,
        accept: (ClassStereotypeSupport) -> Boolean,
    ): StereotypeMatch? {
        if (!element.isValid) return null
        if (element is PsiFile) {
            // Files shown with Emberkai artwork instead of native class icons stay undecorated.
            val file = element.virtualFile ?: return null
            if (!NativeIconPolicy.usesFileIcon(file, file.name)) return null
        }
        // Supports reject unrelated elements by type first, so most icons never reach the file index.
        return ClassStereotypeSupport.EP_NAME.extensionList
            .firstNotNullOfOrNull { support -> support.takeIf(accept)?.badgeFor(element) }
            ?.takeIf { isProjectSource(element) }
    }

    private fun isProjectSource(element: PsiElement): Boolean {
        val file = PsiUtilCore.getVirtualFile(element) ?: return false
        return ProjectFileIndex.getInstance(element.project).isInSourceContent(file)
    }
}

/**
 * Adds the stereotype glyph as an extra layer when the platform builds a native element icon, so
 * IntelliJ's type icon, modifier marks, and visibility column stay exactly as they were. The glyph
 * sits in the top-left corner: static marks use the bottom-left, runnable and test marks the
 * top-right, and lock marks the bottom-right. Final Java classes, whose mark shares the top-left,
 * are left unbadged.
 */
class ClassStereotypeLayerProvider : IconLayerProvider {
    override fun getLayerIcon(element: Iconable, isLocked: Boolean): Icon? {
        if (element !is PsiElement) return null
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.STEREOTYPE_BADGES)) return null
        return ClassStereotypes.layerFor(element)?.stereotype?.icon
    }

    override fun getLayerDescription(): String = "Class stereotype"
}
