package com.mattbrady.checklist.data

/**
 * Client-side mirror of the parsing rule enforced server-side
 * (backend/src/index.ts: parseNoteText). Used only to show an immediate
 * preview / validation message in the capture UI — the server re-parses
 * and is the source of truth for the final category/subcategory names.
 *
 * Rule: first word = category, second word = subcategory, everything after
 * that = the note body. Needs at least 3 words total.
 */
object NoteParser {

    data class Preview(val category: String, val subcategory: String, val body: String)

    fun preview(raw: String): Preview? {
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 3) return null
        val category = words[0]
        val subcategory = words[1]
        val body = words.drop(2).joinToString(" ")
        return Preview(category, subcategory, body)
    }
}
