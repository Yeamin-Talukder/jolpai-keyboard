package com.yeamin.jolpai.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One clipboard history entry: text, or an image saved as a private file at [imagePath].
 * Pinned entries are never removed automatically.
 */
@Entity(tableName = "clip")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String?,
    val imagePath: String?,
    val mimeType: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val pinned: Boolean = false,
    /** A user-chosen folder ("Work", "Addresses" …), or null. */
    val folder: String? = null,
) {
    val isImage: Boolean get() = imagePath != null
}

/** How long unpinned clipboard entries are kept. */
enum class ClipRetention(val storageKey: String, val millis: Long?, val label: String) {
    HOUR("1h", 60 * 60 * 1000L, "1 hour"),
    DAY("1d", 24 * 60 * 60 * 1000L, "1 day"),
    WEEK("7d", 7 * 24 * 60 * 60 * 1000L, "7 days"),
    MONTH("30d", 30 * 24 * 60 * 60 * 1000L, "30 days"),
    FOREVER("forever", null, "Until I delete them");

    companion object {
        val DEFAULT = DAY
        fun fromStorageKey(key: String?): ClipRetention = entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

/** Saved text the user reuses. Typing [shortcut] then space expands to [text]. */
@Entity(tableName = "snippet")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val shortcut: String?,
    val text: String,
    val createdAt: Long,
    /** A [SnippetCategory] storage key. */
    @ColumnInfo(defaultValue = "other") val category: String = "other",
) {
    val kind: SnippetCategory get() = SnippetCategory.fromStorageKey(category)
}

@Dao
interface ClipboardDao {
    @Insert suspend fun insert(clip: ClipEntity): Long
    @Update suspend fun update(clip: ClipEntity)

    @Query("SELECT * FROM clip ORDER BY pinned DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clip ORDER BY createdAt DESC LIMIT 1")
    suspend fun newest(): ClipEntity?

    @Query("SELECT * FROM clip WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipEntity?

    @Query("SELECT * FROM clip WHERE id = :id")
    suspend fun byId(id: Long): ClipEntity?

    @Query("UPDATE clip SET lastUsedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE clip SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE clip SET folder = :folder WHERE id = :id")
    suspend fun setFolder(id: Long, folder: String?)

    @Query("DELETE FROM clip WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clip WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT * FROM clip WHERE pinned = 0 AND lastUsedAt < :cutoff")
    suspend fun unpinnedOlderThan(cutoff: Long): List<ClipEntity>

    /** Unpinned entries beyond the newest [keep], oldest first. */
    @Query("SELECT * FROM clip WHERE pinned = 0 ORDER BY lastUsedAt DESC LIMIT -1 OFFSET :keep")
    suspend fun unpinnedBeyond(keep: Int): List<ClipEntity>

    @Query("SELECT * FROM clip WHERE pinned = 0")
    suspend fun allUnpinned(): List<ClipEntity>

    @Query("SELECT DISTINCT folder FROM clip WHERE folder IS NOT NULL ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>
}

@Dao
interface SnippetDao {
    @Insert suspend fun insert(snippet: SnippetEntity): Long
    @Update suspend fun update(snippet: SnippetEntity)

    @Query("DELETE FROM snippet WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM snippet")
    suspend fun all(): List<SnippetEntity>

    @Query("SELECT * FROM snippet ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SnippetEntity>>
}

/** How often and how recently an emoji (or kaomoji) was used, and whether it is a favourite. */
@Entity(tableName = "emoji_usage")
data class EmojiUsageEntity(
    @PrimaryKey val emoji: String,
    val count: Int,
    val lastUsed: Long,
    val favorite: Boolean = false,
)

@Dao
interface EmojiDao {
    @Query("SELECT * FROM emoji_usage WHERE count > 0 ORDER BY lastUsed DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EmojiUsageEntity>>

    @Query("SELECT * FROM emoji_usage WHERE favorite = 1 ORDER BY lastUsed DESC")
    fun observeFavorites(): Flow<List<EmojiUsageEntity>>

    @Query("SELECT * FROM emoji_usage WHERE emoji = :emoji")
    suspend fun get(emoji: String): EmojiUsageEntity?

    @androidx.room.Upsert
    suspend fun upsert(e: EmojiUsageEntity)

    @Query("DELETE FROM emoji_usage")
    suspend fun clear()
}

/** A theme the user made: [json] is the theme-engine export format; the image is a private file. */
@Entity(tableName = "custom_theme")
data class CustomThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val json: String,
    val backgroundImage: String? = null,
    val updatedAt: Long,
)

@Dao
interface ThemeDao {
    @Query("SELECT * FROM custom_theme ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomThemeEntity>>

    @Query("SELECT * FROM custom_theme")
    suspend fun all(): List<CustomThemeEntity>

    @Query("SELECT * FROM custom_theme WHERE id = :id")
    suspend fun get(id: Long): CustomThemeEntity?

    @Insert suspend fun insert(e: CustomThemeEntity): Long
    @Update suspend fun update(e: CustomThemeEntity)

    @Query("DELETE FROM custom_theme WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 3 → 4: adds custom themes. */
/** v5: snippets get a category. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE snippet ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_theme` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `json` TEXT NOT NULL, " +
                "`backgroundImage` TEXT, `updatedAt` INTEGER NOT NULL)",
        )
    }
}

/** Version 2 → 3: adds emoji recents and favourites. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `emoji_usage` (`emoji` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                "`lastUsed` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, PRIMARY KEY(`emoji`))",
        )
    }
}

/** Version 1 → 2: adds clipboard history and snippets. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `clip` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT, " +
                "`imagePath` TEXT, `mimeType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                "`pinned` INTEGER NOT NULL, `folder` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snippet` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`shortcut` TEXT, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}
