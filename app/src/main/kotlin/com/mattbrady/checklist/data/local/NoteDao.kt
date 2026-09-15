package com.mattbrady.checklist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mattbrady.checklist.data.remote.NoteDto
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM notes WHERE deleted = 0 AND pendingDelete = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE serverId IS NULL AND pendingDelete = 0")
    suspend fun getPendingNew(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE serverId IS NOT NULL AND pendingSync = 1 AND pendingDelete = 0")
    suspend fun getPendingUpdates(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE pendingDelete = 1")
    suspend fun getPendingDeletes(): List<NoteEntity>

    @Query(
        """
        UPDATE notes SET serverId = :serverId, category = :category, subcategory = :subcategory,
        body = :body, updatedAt = :updatedAt, pendingSync = 0 WHERE localId = :localId
        """
    )
    suspend fun markSynced(
        localId: Long,
        serverId: Long,
        category: String,
        subcategory: String,
        body: String,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET pendingSync = 0 WHERE localId = :localId")
    suspend fun clearPendingSync(localId: Long)

    @Query("UPDATE notes SET done = :done, updatedAt = :updatedAt, pendingSync = 1 WHERE localId = :localId")
    suspend fun setDoneLocal(localId: Long, done: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET pendingDelete = 1, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun markPendingDelete(localId: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE localId = :localId")
    suspend fun hardDelete(localId: Long)

    @Query("SELECT * FROM notes WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: Long): NoteEntity?

    @Query(
        """
        UPDATE notes SET category = :category, subcategory = :subcategory, body = :body,
        done = :done, updatedAt = :updatedAt, deleted = :deleted WHERE serverId = :serverId
        """
    )
    suspend fun updateFromServer(
        serverId: Long,
        category: String,
        subcategory: String,
        body: String,
        done: Boolean,
        updatedAt: Long,
        deleted: Boolean,
    )

    /**
     * Pulls the server's note list into the local cache: updates notes we
     * already know about (matched by serverId) and inserts ones we've never
     * seen locally. Without this, a fresh install (or a note added from
     * another device, e.g. a shared token) never shows up in the app's own
     * list - it only ever shows notes created on THIS device since the local
     * database was last created - even though the widget's category counts
     * come straight from the server and correctly include everything. That
     * mismatch is what made the widget look "stuck" on old data: the old
     * data was real, the app just had no way to show or delete it.
     */
    @Transaction
    suspend fun mergeFromServer(notes: List<NoteDto>) {
        for (n in notes) {
            val existing = findByServerId(n.id)
            if (existing != null) {
                // A local change not yet pushed wins until it has synced -
                // don't let the pull clobber it out from under the push.
                if (existing.pendingSync || existing.pendingDelete) continue
                updateFromServer(
                    serverId = n.id,
                    category = n.category,
                    subcategory = n.subcategory,
                    body = n.body,
                    done = n.done != 0,
                    updatedAt = n.updatedAt,
                    deleted = n.deleted != 0,
                )
            } else if (n.deleted == 0) {
                insert(
                    NoteEntity(
                        serverId = n.id,
                        rawText = "${n.category} ${n.subcategory} ${n.body}",
                        category = n.category,
                        subcategory = n.subcategory,
                        body = n.body,
                        done = n.done != 0,
                        createdAt = n.createdAt,
                        updatedAt = n.updatedAt,
                        pendingSync = false,
                        pendingDelete = false,
                        deleted = false,
                    )
                )
            }
        }
    }
}
