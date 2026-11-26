package com.github.khopland.emberkai.ui

import com.github.khopland.emberkai.audit.IconLegendDialog
import com.github.khopland.emberkai.core.RefreshProjectIconPresentations
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.SettingsPreset
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Introduces the plugin once, in the first project opened after it is installed: most of what it
 * does sits behind a preset and a legend that nobody finds on their own. Users who already changed
 * the features, so who found the settings page, are not told about it.
 */
internal object WelcomeNotification {
    const val GROUP_ID = "Emberkai Icons"
    internal const val SHOWN_KEY = "emberkai.icons.welcomeShown"

    /** Whether to introduce the plugin now; marks it introduced either way, so it is asked once. */
    fun claim(properties: PropertiesComponent = PropertiesComponent.getInstance()): Boolean {
        if (properties.isTrueValue(SHOWN_KEY)) return false
        properties.setValue(SHOWN_KEY, true)
        return SettingsPreset.matching(EmberkaiIconsSettings.getInstance().state) == SettingsPreset.RECOMMENDED
    }

    fun create(): Notification =
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID).createNotification(
            "Emberkai Icons is on",
            "Besides file icons, Emberkai marks production config, unignored secrets, and the role of each " +
                "class: controllers, listeners, jobs, repositories, entities, and tests. " +
                "The legend shows what every mark means.",
            NotificationType.INFORMATION,
        ).apply {
            addAction(NotificationAction.create("Show icon legend") { event, _ -> IconLegendDialog(event.project).show() })
            addAction(presetAction(SettingsPreset.ICONS_ONLY, "Just the icons"))
            addAction(presetAction(SettingsPreset.EVERYTHING, "Turn everything on"))
            addAction(NotificationAction.create("Settings") { event, _ -> openSettings(event.project) })
        }

    /** Applies [preset] to the features, as the settings page's Preset picker does. */
    internal fun apply(preset: SettingsPreset) {
        val settings = EmberkaiIconsSettings.getInstance()
        settings.loadState(preset.applyTo(settings.state))
        RefreshProjectIconPresentations.refresh()
    }

    private fun presetAction(preset: SettingsPreset, text: String) =
        NotificationAction.create(text) { event, notification ->
            apply(preset)
            notification.expire()
            NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID).createNotification(
                "Emberkai Icons preset: ${preset.displayName}",
                preset.description,
                NotificationType.INFORMATION,
            ).apply {
                addAction(NotificationAction.createSimpleExpiring("Change in settings") { openSettings(event.project) })
            }.notify(event.project)
        }

    private fun openSettings(project: Project?) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, EmberkaiIconsConfigurable::class.java)
    }
}

/** Shows [WelcomeNotification] once the first project has opened. */
internal class WelcomeNotificationActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (!WelcomeNotification.claim()) return
        withContext(Dispatchers.EDT) {
            if (!project.isDisposed) WelcomeNotification.create().notify(project)
        }
    }
}
