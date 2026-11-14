package fr.calevent.core

/** Review status of a detected slot, derived from the recognition confidence. */
enum class SlotStatus {
    /** Confidence >= 0.8 and a certain date: pre-selected for export. */
    KEPT,

    /** Confidence in [0.5, 0.8) or an uncertain date: highlighted, not selected. */
    TO_REVIEW,

    /** Confidence < 0.5: hidden by default. */
    IGNORED;

    companion object {
        const val KEPT_THRESHOLD = 0.8
        const val REVIEW_THRESHOLD = 0.5

        fun from(confidence: Double, dateUncertain: Boolean = false): SlotStatus = when {
            confidence < REVIEW_THRESHOLD -> IGNORED
            confidence < KEPT_THRESHOLD || dateUncertain -> TO_REVIEW
            else -> KEPT
        }
    }
}
