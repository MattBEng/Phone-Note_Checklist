package com.mattbrady.checklist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
)

@Entity(tableName = "subcategories")
data class SubcategoryEntity(
    @PrimaryKey val id: Long,
    val categoryId: Long,
    val name: String,
    val openCount: Long,
    val totalCount: Long,
)

/**
 * A single checklist item. Created optimistically and locally the moment
 * the user hits Save (so the widget/app feel instant, even offline);
 * [category]/[subcategory]/[body] start out as a local best-guess parse of
 * [rawText] and get overwritten with the server's canonical values once
 * synced (see ChecklistRepository.syncNow).
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val rawText: String,
    val category: String,
    val subcategory: String,
    val body: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** True when there's a local change (new note, or a done/body edit) not yet pushed. */
    val pendingSync: Boolean,
    /** True when the user deleted this locally but it hasn't been deleted server-side yet. */
    val pendingDelete: Boolean,
    val deleted: Boolean,
)
