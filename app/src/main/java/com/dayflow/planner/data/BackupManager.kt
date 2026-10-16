package com.dayflow.planner.data

import android.content.Context
import com.dayflow.planner.logic.Dates
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-database JSON export / import (offline, user-triggered).
 *
 * Format (v1):
 * { "version": 1, "exportedAt": "...",
 *   "tasks": [...], "subtasks": [...], "completions": [...],
 *   "habits": [...], "checkIns": [...], "notes": [...],
 *   "diary": [...], "goals": [...], "settings": {...} }
 */
class BackupManager(
    private val context: Context,
    private val repository: Repository,
    private val settings: com.dayflow.planner.settings.SettingsStore,
) {

    suspend fun export(target: OutputStream) {
        val snapshot = repository.exportAll()
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "DayFlow Planner")
        root.put("exportedAt", LocalDate.now().toString())

        root.put("tasks", JSONArray().apply {
            snapshot.tasks.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("notes", it.notes)
                    put("priority", it.priority)
                    putIt("dueDay", it.dueDay)
                    putIt("dueTime", it.dueTime)
                    putIt("completedAt", it.completedAt)
                    put("recurrence", it.recurrence)
                    put("anchorDay", it.anchorDay)
                    put("tags", JSONArray(it.tags))
                    put("createdAt", it.createdAt)
                    put("updatedAt", it.updatedAt)
                })
            }
        })
        root.put("subtasks", JSONArray().apply {
            snapshot.subtasks.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("taskId", it.taskId); put("title", it.title)
                    put("done", it.done); put("position", it.position)
                })
            }
        })
        root.put("completions", JSONArray().apply {
            snapshot.completions.forEach {
                put(JSONObject().apply {
                    put("taskId", it.taskId); put("dayEpoch", it.dayEpoch); put("completedAt", it.completedAt)
                })
            }
        })
        root.put("habits", JSONArray().apply {
            snapshot.habits.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("description", it.description)
                    put("emoji", it.emoji); put("colorArgb", it.colorArgb); put("daysMask", it.daysMask)
                    put("archived", it.archived); put("createdAt", it.createdAt)
                })
            }
        })
        root.put("checkIns", JSONArray().apply {
            snapshot.checkIns.forEach {
                put(JSONObject().apply {
                    put("habitId", it.habitId); put("dayEpoch", it.dayEpoch); put("createdAt", it.createdAt)
                })
            }
        })
        root.put("notes", JSONArray().apply {
            snapshot.notes.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("title", it.title); put("body", it.body)
                    put("pinned", it.pinned); put("favorite", it.favorite)
                    put("tags", JSONArray(it.tags)); put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                })
            }
        })
        root.put("diary", JSONArray().apply {
            snapshot.diary.forEach {
                put(JSONObject().apply {
                    put("dayEpoch", it.dayEpoch)
                    if (it.mood != null) put("mood", it.mood)
                    put("text", it.text); put("updatedAt", it.updatedAt)
                })
            }
        })
        root.put("goals", JSONArray().apply {
            snapshot.goals.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("title", it.title); put("description", it.description)
                    putIt("deadlineDay", it.deadlineDay); put("progress", it.progress)
                    put("done", it.done); put("createdAt", it.createdAt)
                })
            }
        })
        root.put("settings", settings.toJson())

        target.writer().use { writer -> writer.append(root.toString(2)) }
    }

    suspend fun import(source: InputStream, wipeFirst: Boolean = true): ImportSummary {
        val text = source.bufferedReader().use(BufferedReader::readText)
        val root = try {
            JSONObject(text)
        } catch (e: org.json.JSONException) {
            return ImportSummary(false, "File is not valid JSON: ${e.message?.take(80)}")
        }
        if (root.optInt("version", -1) != 1) {
            return ImportSummary(false, "Unsupported backup version (expected 1)")
        }
        return try {
            val snapshot = DbSnapshot(
                tasks = readTasks(root.optJSONArray("tasks")),
                subtasks = readSubtasks(root.optJSONArray("subtasks")),
                completions = readCompletions(root.optJSONArray("completions")),
                habits = readHabits(root.optJSONArray("habits")),
                checkIns = readCheckIns(root.optJSONArray("checkIns")),
                notes = readNotes(root.optJSONArray("notes")),
                diary = readDiary(root.optJSONArray("diary")),
                goals = readGoals(root.optJSONArray("goals")),
            )
            repository.importAll(snapshot, wipeFirst)
            settings.importJson(root.optJSONObject("settings"))
            ImportSummary(
                true,
                "Imported ${snapshot.tasks.size} tasks, ${snapshot.habits.size} habits, " +
                    "${snapshot.notes.size} notes, ${snapshot.goals.size} goals",
            )
        } catch (e: Exception) {
            ImportSummary(false, "Import failed: ${e.message?.take(120)}")
        }
    }

    private fun readTasks(arr: JSONArray?): List<TaskEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                TaskEntity(
                    id = obj.optLong("id"),
                    title = obj.optString("title"),
                    notes = obj.optString("notes"),
                    priority = obj.optInt("priority", 1),
                    dueDay = obj.optLongOrNull("dueDay"),
                    dueTime = obj.optIntOrNull("dueTime"),
                    completedAt = obj.optLongOrNull("completedAt"),
                    recurrence = obj.optString("recurrence", "NONE"),
                    anchorDay = obj.optLong("anchorDay"),
                    tags = obj.optJSONArray("tags").stringList(),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                ),
            )
        }
    }

    private fun readSubtasks(arr: JSONArray?): List<SubtaskEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                SubtaskEntity(
                    id = obj.optLong("id"),
                    taskId = obj.optLong("taskId"),
                    title = obj.optString("title"),
                    done = obj.optBoolean("done"),
                    position = obj.optInt("position"),
                ),
            )
        }
    }

    private fun readCompletions(arr: JSONArray?): List<TaskCompletionEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(TaskCompletionEntity(obj.optLong("taskId"), obj.optLong("dayEpoch"), obj.optLong("completedAt")))
        }
    }

    private fun readHabits(arr: JSONArray?): List<HabitEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                HabitEntity(
                    id = obj.optLong("id"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    emoji = obj.optString("emoji", "✅"),
                    colorArgb = obj.optLong("colorArgb", 0xFF5B5BD6L),
                    daysMask = obj.optInt("daysMask", 0b1111111),
                    archived = obj.optBoolean("archived"),
                    createdAt = obj.optLong("createdAt"),
                ),
            )
        }
    }

    private fun readCheckIns(arr: JSONArray?): List<HabitCheckInEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(HabitCheckInEntity(obj.optLong("habitId"), obj.optLong("dayEpoch"), obj.optLong("createdAt")))
        }
    }

    private fun readNotes(arr: JSONArray?): List<NoteEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                NoteEntity(
                    id = obj.optLong("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    pinned = obj.optBoolean("pinned"),
                    favorite = obj.optBoolean("favorite"),
                    tags = obj.optJSONArray("tags").stringList(),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                ),
            )
        }
    }

    private fun readDiary(arr: JSONArray?): List<DiaryEntryEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                DiaryEntryEntity(
                    dayEpoch = obj.optLong("dayEpoch"),
                    mood = obj.optIntOrNull("mood"),
                    text = obj.optString("text"),
                    updatedAt = obj.optLong("updatedAt"),
                ),
            )
        }
    }

    private fun readGoals(arr: JSONArray?): List<GoalEntity> = buildList {
        arr.orEmpty().forEach { o ->
            val obj = o as JSONObject
            add(
                GoalEntity(
                    id = obj.optLong("id"),
                    title = obj.optString("title"),
                    description = obj.optString("description"),
                    deadlineDay = obj.optLongOrNull("deadlineDay"),
                    progress = obj.optInt("progress"),
                    done = obj.optBoolean("done"),
                    createdAt = obj.optLong("createdAt"),
                ),
            )
        }
    }

    data class ImportSummary(val ok: Boolean, val message: String)

    // --- small JSON helpers ----------------------------------------------------

    private fun JSONObject.putIt(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun JSONArray?.stringList(): List<String> =
        this.orEmpty().let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
}

private fun JSONArray.forEach(block: (Any?) -> Unit) {
    for (i in 0 until length()) block(opt(i))
}
