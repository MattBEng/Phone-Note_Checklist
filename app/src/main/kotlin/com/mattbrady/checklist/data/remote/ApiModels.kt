package com.mattbrady.checklist.data.remote

import kotlinx.serialization.Serializable

// These mirror the JSON shapes returned by the Cloudflare Worker in
// backend/src/index.ts exactly — field names must match.

@Serializable
data class CreateNoteRequest(val text: String)

@Serializable
data class CreateNoteResponse(
    val id: Long,
    val category: String,
    val categoryCreated: Boolean,
    val subcategory: String,
    val subcategoryCreated: Boolean,
    val body: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class NoteDto(
    val id: Long,
    val body: String,
    val done: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Int,
    val subcategoryId: Long,
    val subcategory: String,
    val categoryId: Long,
    val category: String,
)

@Serializable
data class NotesResponse(val notes: List<NoteDto>)

@Serializable
data class UpdateNoteRequest(val done: Boolean? = null, val body: String? = null)

@Serializable
data class UpdateNoteResponse(val id: Long, val updatedAt: Long)

@Serializable
data class DeleteNoteResponse(val id: Long, val deleted: Boolean)

@Serializable
data class SubcategoryNode(
    val id: Long,
    val name: String,
    val openCount: Long,
    val totalCount: Long,
)

@Serializable
data class CategoryNode(
    val category: String,
    val categoryId: Long,
    val subcategories: List<SubcategoryNode> = emptyList(),
)

@Serializable
data class CategoryTreeResponse(val categories: List<CategoryNode>)
