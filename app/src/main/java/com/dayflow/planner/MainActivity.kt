package com.dayflow.planner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.ui.MoreScreen
import com.dayflow.planner.ui.calendar.CalendarScreen
import com.dayflow.planner.ui.diary.DiaryScreen
import com.dayflow.planner.ui.goals.GoalsScreen
import com.dayflow.planner.ui.habits.HabitDetailScreen
import com.dayflow.planner.ui.habits.HabitsScreen
import com.dayflow.planner.ui.notes.NoteEditorScreen
import com.dayflow.planner.ui.notes.NotesScreen
import com.dayflow.planner.ui.settings.SettingsScreen
import com.dayflow.planner.ui.stats.StatsScreen
import com.dayflow.planner.ui.tasks.TaskEditorScreen
import com.dayflow.planner.ui.tasks.TasksScreen
import com.dayflow.planner.ui.today.TodayScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Route the UI should jump to because of a notification tap or deep link. */
    private val pendingRoute = mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* user choice only affects reminders; no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseRoute(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val app = DayFlowApp.from(this)
            val settings = app.container.settings
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val accent by settings.accentIndex.collectAsStateWithLifecycle()
            com.dayflow.planner.ui.theme.DayFlowTheme(mode = mode, accentIndex = accent) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNav(
                        pendingRoute = pendingRoute,
                        onPendingConsumed = { pendingRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseRoute(intent)
    }

    private fun parseRoute(intent: Intent?) {
        intent ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val day = intent.getLongExtra(EXTRA_OPEN_DAY, -1L)
        val data = intent.data
        var id = if (taskId >= 0) taskId else -1L
        var d = if (day >= 0) day else -1L
        if (data?.scheme == "dayflow" && data.host == "reminder") {
            if (id < 0) id = data.getQueryParameter("task")?.toLongOrNull() ?: -1L
            if (d < 0) d = data.getQueryParameter("day")?.toLongOrNull() ?: -1L
        }
        when {
            id >= 0 && d >= 0 -> pendingRoute.value = "task/$id?preset=$d"
            id >= 0 -> pendingRoute.value = "task/$id?preset=-1"
            d >= 0 -> pendingRoute.value = "calendar?focus=$d"
            else -> {}
        }
        intent.removeExtra(EXTRA_TASK_ID)
        intent.removeExtra(EXTRA_OPEN_DAY)
    }

    companion object {
        const val EXTRA_OPEN_DAY = "com.dayflow.planner.extra.OPEN_DAY"
        const val EXTRA_TASK_ID = "com.dayflow.planner.extra.TASK_ID"
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun AppNav(
    pendingRoute: MutableState<String?>,
    onPendingConsumed: () -> Unit,
) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabs = listOf(
        Tab("today", "Today", Icons.Rounded.Home),
        Tab("tasks", "Tasks", Icons.Rounded.List),
        Tab("calendar", "Calendar", Icons.Rounded.CalendarMonth),
        Tab("habits", "Habits", Icons.Rounded.SelfImprovement),
        Tab("more", "More", Icons.Rounded.MoreHoriz),
    )
    val showTabs = currentRoute in tabs.map { it.route }

    LaunchedEffect(pendingRoute.value) {
        pendingRoute.value?.let {
            nav.navigate(it) { launchSingleTop = true }
            onPendingConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("today") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == "tasks") {
                FloatingActionButton(onClick = { nav.navigate("task/0?preset=-1") }) {
                    Icon(Icons.Rounded.Add, "New task")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = "today") {
                composable("today") {
                    TodayScreen(
                        onOpenTasks = { nav.navigate("tasks") { popUpTo("today") } },
                        onOpenTask = { id -> nav.navigate("task/$id?preset=-1") },
                        onOpenHabit = { id -> nav.navigate("habit/$id") },
                        onQuickAdd = {
                            nav.navigate("task/0?preset=${Dates.toEpochDay(LocalDate.now())}")
                        },
                        onOpenDiary = { nav.navigate("diary?day=-1") },
                    )
                }
                composable("tasks") {
                    TasksScreen(onOpenTask = { id -> nav.navigate("task/$id?preset=-1") })
                }
                composable(
                    "calendar?focus={focus}",
                    arguments = listOf(navArgument("focus") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val focusArg = entry.arguments?.getLong("focus") ?: -1L
                    CalendarScreen(
                        focusEpoch = if (focusArg >= 0) focusArg else null,
                        onOpenTask = { id -> nav.navigate("task/$id?preset=-1") },
                        onNewTask = { dayEpoch -> nav.navigate("task/0?preset=$dayEpoch") },
                        onOpenDiary = { dayEpoch -> nav.navigate("diary?day=$dayEpoch") },
                        onOpenNote = { noteId -> nav.navigate("note/$noteId") },
                    )
                }
                composable("habits") {
                    HabitsScreen(onOpenHabit = { id -> nav.navigate("habit/$id") })
                }
                composable(
                    "habit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    HabitDetailScreen(
                        habitId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "task/{id}?preset={preset}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("preset") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    val preset = entry.arguments?.getLong("preset") ?: -1L
                    TaskEditorScreen(
                        taskId = id,
                        presetDay = if (preset >= 0) preset else null,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("notes") {
                    NotesScreen(
                        onOpenNote = { id -> nav.navigate("note/$id") },
                        onNewNote = { nav.navigate("note/0") },
                    )
                }
                composable(
                    "note/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    NoteEditorScreen(
                        noteId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "diary?day={day}",
                    arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val day = entry.arguments?.getLong("day")
                    DiaryScreen(
                        initialDayEpoch = if (day != null && day >= 0) day else null,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("goals") { GoalsScreen() }
                composable("stats") { StatsScreen() }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable("more") {
                    MoreScreen(
                        onOpenNotes = { nav.navigate("notes") },
                        onOpenDiary = { nav.navigate("diary?day=-1") },
                        onOpenGoals = { nav.navigate("goals") },
                        onOpenHabits = { nav.navigate("habits") },
                        onOpenStats = { nav.navigate("stats") },
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
            }
        }
    }
}
