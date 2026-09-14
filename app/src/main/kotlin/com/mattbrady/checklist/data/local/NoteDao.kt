package com.mattbrady.checklist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}
