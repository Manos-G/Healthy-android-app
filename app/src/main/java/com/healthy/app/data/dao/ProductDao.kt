package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthy.app.data.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: Product)

    /**
     * The local-first lookup (spec 11.2). A hit means the app makes no network
     * request at all, which is what keeps a scan working in flight mode.
     */
    @Query("SELECT * FROM product WHERE barcode = :barcode")
    suspend fun byBarcode(barcode: String): Product?

    @Query("SELECT EXISTS(SELECT 1 FROM product WHERE barcode = :barcode)")
    suspend fun isKnown(barcode: String): Boolean

    /** Name search over hand-entered and scanned items alike (spec 12.4). */
    @Query("SELECT * FROM product WHERE name LIKE '%' || :term || '%' ORDER BY name ASC LIMIT :limit")
    fun search(term: String, limit: Int = 50): Flow<List<Product>>

    @Query("SELECT * FROM product WHERE kind = :kind ORDER BY name ASC")
    fun observeByKind(kind: String): Flow<List<Product>>

    @Query("SELECT * FROM product ORDER BY barcode ASC")
    suspend fun allForExport(): List<Product>

    @Query("DELETE FROM product WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
