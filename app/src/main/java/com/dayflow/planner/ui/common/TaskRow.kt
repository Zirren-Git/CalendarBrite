package com.dayflow.planner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import com.dayflow.planner.data.Priorities
import com.dayflow.planner.logic.TaskInstance
import com.dayflow.planner.logic.Times

/** Colour coding for the three priorities (original DayFlow ramp). */
fun priorityColor(priority: Int): Color = when (priority) {
    Priorities.HIGH -> Color(0xFFD14343)
    Priorities.MEDIUM -> Color(0xFFE0A020)
    else -> Color(0xFF7FA37F)
}

@Composable
fun TaskRow(
    instance: TaskInstance,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
) {
    val task = instance.task
    if (onDelete != null) {
        SwipeableTaskRow(onDelete = onDelete) {
            TaskRowContent(instance, onToggle, onOpen, onMenu)
        }
    } else {
        TaskRowContent(instance, onToggle, onOpen, onMenu, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // snap back; the row disappears via state updates instead
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        content()
    }
}

@Composable
private fun TaskRowContent(
    instance: TaskInstance,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val task = instance.task
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (instance.done) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = instance.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (instance.done) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(priorityColor(task.priority), CircleShape),
                    )
                    Text(
                        Priorities.label(task.priority),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.dueTime != null && !instance.done) {
                        Text(
                            Times.label(task.dueTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (instance.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (instance.overdue) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    if (instance.recurring) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = "Recurring",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (instance.subtasksTotal > 0) {
                        Text(
                            "☑ ${instance.subtasksDone}/${instance.subtasksTotal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.tags.isNotEmpty()) {
                        Text(
                            task.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (instance.overdue) {
                Text(
                    "Overdue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
            }
            if (onMenu != null) {
                IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
