package com.dayflow.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class MoreItem(
    val emoji: String? = null,
    val icon: ImageVector? = null,
    val title: String,
    val subtitle: String,
    val tintKey: Int,
)

@Composable
fun MoreScreen(
    onOpenNotes: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        Triple(MoreItem(icon = Icons.Rounded.Description, title = "Notes", subtitle = "Ideas, lists, snippets — anything loosely pinned", tintKey = 0), "notes", onOpenNotes),
        Triple(MoreItem(icon = Icons.Rounded.EditNote, title = "Journal", subtitle = "One line a day and a mood dot", tintKey = 1), "diary", onOpenDiary),
        Triple(MoreItem(icon = Icons.Rounded.Flag, title = "Goals", subtitle = "Long-horizon targets with progress sliders", tintKey = 2), "goals", onOpenGoals),
        Triple(MoreItem(icon = Icons.Rounded.SelfImprovement, title = "Habits", subtitle = "Streaks, heatmaps and a ready-made library", tintKey = 3), "habits", onOpenHabits),
        Triple(MoreItem(icon = Icons.Rounded.AutoGraph, title = "Statistics", subtitle = "Weekly bars, consistency heat, mood average", tintKey = 4), "stats", onOpenStats),
        Triple(MoreItem(icon = Icons.Rounded.Settings, title = "Settings", subtitle = "Theme, accents, reminders, local backup", tintKey = 5), "settings", onOpenSettings),
    )

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tiles.forEach { (item, _, onClick) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onClick),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.icon != null) {
                                    Icon(
                                        item.icon,
                                        null,
                                        Modifier.size(21.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(item.emoji ?: "•")
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

