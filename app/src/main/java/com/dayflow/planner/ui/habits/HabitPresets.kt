package com.dayflow.planner.ui.habits

import com.dayflow.planner.logic.HabitSchedule
import java.time.DayOfWeek

/** A one-tap template from the habit library. */
data class HabitPreset(
    val name: String,
    val emoji: String,
    val description: String,
    val daysMask: Int,
)

/**
 * ~20 common starter habits. All daily unless noted; "weekdays"/"weekend"
 * presets map onto the weekday mask helpers.
 */
object HabitPresets {

    private val WEEKDAYS = HabitSchedule.maskOf(
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    )

    val ALL: List<HabitPreset> = listOf(
        HabitPreset("Drink enough water", "💧", "Eight glasses spread through the day", HabitSchedule.DAILY_MASK),
        HabitPreset("Exercise", "🏃", "Any workout that raises your heart rate", HabitSchedule.DAILY_MASK),
        HabitPreset("Meditate", "🧘", "Five quiet minutes, guided or not", HabitSchedule.DAILY_MASK),
        HabitPreset("Read", "📚", "At least ten pages of a real book", HabitSchedule.DAILY_MASK),
        HabitPreset("Sleep by 23:00", "😴", "Lights out at a reasonable hour", HabitSchedule.DAILY_MASK),
        HabitPreset("Write in a journal", "📓", "Three honest lines about the day", HabitSchedule.DAILY_MASK),
        HabitPreset("Take a walk", "🚶", "Twenty minutes outside beats the treadmill", HabitSchedule.DAILY_MASK),
        HabitPreset("Stretch", "🤸", "Loosen up after waking or before bed", HabitSchedule.DAILY_MASK),
        HabitPreset("No sugary drinks", "🥤", "Water or tea instead of soda", HabitSchedule.DAILY_MASK),
        HabitPreset("Take vitamins", "💊", "With breakfast, every day", HabitSchedule.DAILY_MASK),
        HabitPreset("Skincare routine", "🧴", "Cleanse, moisturise, sunscreen", HabitSchedule.DAILY_MASK),
        HabitPreset("Limit coffee", "☕", "No more than two cups a day", HabitSchedule.DAILY_MASK),
        HabitPreset("Deep work block", "🎯", "Ninety undistracted minutes", WEEKDAYS),
        HabitPreset("Practice an instrument", "🎸", "Fifteen focused minutes", HabitSchedule.DAILY_MASK),
        HabitPreset("Learn a language", "🗣️", "One lesson or ten new words", HabitSchedule.DAILY_MASK),
        HabitPreset("Call a friend or family", "📞", "A real conversation, not a text", HabitSchedule.DAILY_MASK),
        HabitPreset("Plan tomorrow tonight", "🗓️", "Brain-dump the next day's musts", WEEKDAYS),
        HabitPreset("Screen-free hour", "🌙", "No screens before bed", HabitSchedule.DAILY_MASK),
        HabitPreset("Gratitude list", "🙏", "Name three good things", HabitSchedule.DAILY_MASK),
        HabitPreset("Tidy ten minutes", "🧹", "A small reset for your space", HabitSchedule.DAILY_MASK),
    )
}
