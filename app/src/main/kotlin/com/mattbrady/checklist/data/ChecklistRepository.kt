package com.mattbrady.checklist.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.mattbrady.checklist.data.local.AppDatabase
import com.mattbrady.checklist.data.local.NoteEntity
import com.mattbrady.checklist.data.remote.ApiClientProvider
import com.mattbrady.checklist.data.remote.CreateNoteRequest
import com.mattbrady.checklist.data.remote.UpdateNoteRequest
import com.mattbrady.checklist.sync.SyncScheduler
import com.mattbrady.checklist.widget.ChecklistWidget
import kotlinx.coroutines.flow.Flow

sealed class SyncResult {
    data object Success : SyncResult()
    data object NotConfigured : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

class ChecklistRepository(
    private val db: AppDatabase,
    private val apiProvider: ApiClientProvider,
    private val context: Context,
) {

    fun observeNotes(): Flow<List<NoteEntity>> = db.noteDao().observeActive()

    fun observeCategories() = db.categoryDao().observeCategories()

    fun observeSubcategories() = db.categoryDao().observeSubcategories()

    /**
     * Saves a note locally immediately (so it works offline / feels instant
     * from the widget) and kicks off a background sync. Returns the local
     * preview parse, or null if [rawText] doesn't have enough words yet.
     */
    suspend fun addNote(rawText: String): NoteParser.Preview? {
        val trimmed = rawText.trim()
        val preview = NoteParser.preview(trimmed) ?: return null
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            serverId = null,
            rawText = trimmed,
            category = preview.category,
            subcategory = preview.subcategory,
            body = preview.body,
            done = false,
            createdAt = now,
            updatedAt = now,
            pendingSync = true,
            pendingDelete = false,
            deleted = false,
        )
        db.noteDao().insert(entity)
        SyncScheduler.scheduleSyncNow(context)
        return preview
    }

    suspend fun toggleDone(localId: Long, done: Boolean) {
        db.noteDao().setDoneLocal(localId, done, System.currentTimeMillis())
        SyncScheduler.scheduleSyncNow(context)
    }

    suspend fun deleteNote(localId: Long) {
        db.noteDao().markPendingDelete(localId, System.currentTimeMillis())
        SyncScheduler.scheduleSyncNow(context)
    }

    suspend fun syncNow(): SyncResult {
        val api = apiProvider.getApi() ?: return SyncResult.NotConfigured
        return try {
            for (note in db.noteDao().getPendingNew()) {
                val response = api.createNote(CreateNoteRequest(note.rawText))
                db.noteDao().markSynced(
                    localId = note.localId,
                    serverId = response.id,
                    category = response.category,
                    subcategory = response.subcategory,
                    body = response.body,
                    updatedAt = response.updatedAt,
                )
            }

            for (note in db.noteDao().getPendingUpdates()) {
                val serverId = note.serverId ?: continue
                api.updateNote(serverId, UpdateNoteRequest(done = note.done))
                db.noteDao().clearPendingSync(note.localId)
            }

            for (note in db.noteDao().getPendingDeletes()) {
                note.serverId?.let { api.deleteNote(it) }
                db.noteDao().hardDelete(note.localId)
            }

            val tree = api.getCategories()
            db.categoryDao().replaceTree(tree.categories)

            // Pull the full note list down too, not just push local changes
            // up - otherwise the app's own list only ever shows notes it
            // created itself, while the widget (which reads category counts
            // straight from the server) shows everything, and the two
            // permanently disagree.
            val notesResponse = api.listNotes()
            db.noteDao().mergeFromServer(notesResponse.notes)

            // Redraw the widget too - without this, only the app screen (which
            // watches the database live) updates after a sync. The widget reads
            // a one-off snapshot each time it's drawn, so it needs to be told
            // explicitly, or it just keeps showing whatever it last drew.
            ChecklistWidget().updateAll(context)

            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Sync failed")
        }
    }
}
