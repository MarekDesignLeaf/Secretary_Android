package com.example.secretary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleKey: String, val icon: ImageVector) {
    val title: String get() = when(titleKey) {
        "home" -> Strings.home; "crm" -> Strings.crm; "tasks" -> Strings.tasks
        "calendar" -> Strings.calendar; "settings" -> Strings.settings; else -> titleKey
    }
    object Home : Screen("home", "home", Icons.Default.Home)
    object Crm : Screen("crm", "crm", Icons.Default.Person)
    object Tasks : Screen("tasks", "tasks", Icons.Default.List)
    object Calendar : Screen("calendar", "calendar", Icons.Default.DateRange)
    object Settings : Screen("settings", "settings", Icons.Default.Settings)
    
    object ClientDetail : Screen("client/{clientId}", Strings.editClient, Icons.Default.Person)
    object JobDetail : Screen("job/{jobId}", Strings.job, Icons.Default.Star)
    object TaskDetail : Screen("task/{taskId}", Strings.taskTitle, Icons.Default.List)
}

val navItems = listOf(
    Screen.Home,
    Screen.Crm,
    Screen.Tasks,
    Screen.Calendar,
    Screen.Settings
)
