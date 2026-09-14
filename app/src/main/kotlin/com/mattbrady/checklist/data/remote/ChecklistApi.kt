package com.mattbrady.checklist.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChecklistApi {

    @POST("notes")
    suspend fun createNote(@Body body: CreateNoteRequest): CreateNoteResponse

    @GET("notes")
    suspend fun listNotes(
        @Query("since") since: Long = 0,
        @Query("includeDeleted") includeDeleted: Int = 1,
    ): NotesResponse

    @PATCH("notes/{id}")
    suspend fun updateNote(@Path("id") id: Long, @Body body: UpdateNoteRequest): UpdateNoteResponse

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: Long): DeleteNoteResponse

    @GET("categories")
    suspend fun getCategories(): CategoryTreeResponse
}
