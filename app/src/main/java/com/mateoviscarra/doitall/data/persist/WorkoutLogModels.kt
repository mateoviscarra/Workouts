package com.mateoviscarra.doitall.data.persist

/**
 * Unit used for exercise load weight.
 */
enum class WeightUnit { KG, LBS }

/**
 * Saved training numbers for one [ExerciseDefinition.id]. Shared across all days that use that id.
 */
data class ExerciseLogState(
    val sets: Int,
    val weight: String,
    val usePerSetReps: Boolean,
    val repsSingle: Int?,
    val repsPerSet: List<Int>?,
    val comment: String,
    val isCardio: Boolean,
    val duration: String?,
    val intensity: String?,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val isBodyweight: Boolean = false,
    val usesStraps: Boolean = false,
    val isTimed: Boolean = false,
    val timeTarget: String = "60s"
) {
    companion object {
        const val MAX_COMMENT_LENGTH = 128
    }
}

/**
 * Per-day navigation: carousel position + which catalog id each page is bound to.
 */
data class DayLogState(
    val slots: Map<String, SlotLogState>,
    /** Exercise ids that have been marked as done for this day. */
    val doneExercises: Set<String> = emptySet()
) {
    fun updateSlot(slotIndex: Int, transform: (SlotLogState) -> SlotLogState): DayLogState {
        val key = slotIndex.toString()
        val slot = slots[key] ?: return this
        return copy(slots = slots + (key to transform(slot)))
    }
}

data class SlotLogState(
    val selectedPage: Int,
    /** [exerciseId] per carousel page; must align with slot size from plan. */
    val pageBindings: List<String>
)
