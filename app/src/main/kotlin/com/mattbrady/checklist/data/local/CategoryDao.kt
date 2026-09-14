package com.mattbrady.checklist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mattbrady.checklist.data.remote.CategoryNode
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM subcategories")
    fun observeSubcategories(): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM subcategories")
    suspend fun getSubcategoriesOnce(): List<SubcategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategory(subcategory: SubcategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM subcategories")
    suspend fun clearSubcategories()

    /** Replaces the whole local category/subcategory cache with the server's tree. */
    @Transaction
    suspend fun replaceTree(categories: List<CategoryNode>) {
        clearSubcategories()
        clearCategories()
        for (c in categories) {
            insertCategory(CategoryEntity(id = c.categoryId, name = c.category))
            for (s in c.subcategories) {
                insertSubcategory(
                    SubcategoryEntity(
                        id = s.id,
                        categoryId = c.categoryId,
                        name = s.name,
                        openCount = s.openCount,
                        totalCount = s.totalCount,
                    )
                )
            }
        }
    }
}
