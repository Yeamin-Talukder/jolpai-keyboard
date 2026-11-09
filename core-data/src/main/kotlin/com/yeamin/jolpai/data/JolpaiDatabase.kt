package com.yeamin.jolpai.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A word the user has typed. [key] is the lower-cased word; [word] the latest spelling.
 * [weight] is the use count fading over time (-1: not stored yet, use [count]).
 */
@Entity(tableName = "learned_word", primaryKeys = ["language", "key"])
data class LearnedWordEntity(
    val language: String,
    val key: String,
    val word: String,
    val count: Int,
    val lastUsed: Long,
    @ColumnInfo(defaultValue = "-1") val weight: Float = -1f,
)

/** [word] was typed after [previous] (lower-cased) [count] times; [weight] fades like a word's. */
@Entity(tableName = "learned_bigram", primaryKeys = ["language", "previous", "key"])
data class LearnedBigramEntity(
    val language: String,
    val previous: String,
    val key: String,
    val word: String,
    val count: Int,
    @ColumnInfo(defaultValue = "0") val lastUsed: Long = 0,
    @ColumnInfo(defaultValue = "-1") val weight: Float = -1f,
)

/** [word] was typed after [previous2] [previous] (both lower-cased). */
@Entity(tableName = "learned_trigram", primaryKeys = ["language", "previous2", "previous", "key"])
data class LearnedTrigramEntity(
    val language: String,
    val previous2: String,
    val previous: String,
    val key: String,
    val word: String,
    val count: Int,
    val lastUsed: Long,
    val weight: Float,
)

/** Avro: the Bangla candidate the user picked for a Roman word. */
@Entity(tableName = "avro_selection", primaryKeys = ["roman"])
data class AvroSelectionEntity(val roman: String, val bangla: String)

/** A word the user added to their personal dictionary. */
@Entity(tableName = "personal_word", primaryKeys = ["language", "key"])
data class PersonalWordEntity(val language: String, val key: String, val word: String, val addedAt: Long)

@Dao
interface HistoryDao {
    @Upsert suspend fun upsertWord(e: LearnedWordEntity)
    @Upsert suspend fun upsertBigram(e: LearnedBigramEntity)
    @Upsert suspend fun upsertTrigram(e: LearnedTrigramEntity)
    @Upsert suspend fun upsertSelection(e: AvroSelectionEntity)
    @Upsert suspend fun upsertPersonal(e: PersonalWordEntity)

    @Query("DELETE FROM personal_word WHERE language = :language AND `key` = :key")
    suspend fun deletePersonal(language: String, key: String)

    @Query("DELETE FROM learned_word WHERE language = :language AND `key` = :key")
    suspend fun deleteWord(language: String, key: String)

    @Query("DELETE FROM learned_bigram WHERE language = :language AND `key` = :key")
    suspend fun deleteBigramsTo(language: String, key: String)

    @Query("DELETE FROM learned_trigram WHERE language = :language AND `key` = :key")
    suspend fun deleteTrigramsTo(language: String, key: String)

    @Query("DELETE FROM learned_bigram WHERE language = :language AND previous = :previous AND `key` = :key")
    suspend fun deleteBigram(language: String, previous: String, key: String)

    @Query("DELETE FROM learned_trigram WHERE language = :language AND previous2 = :previous2 AND previous = :previous AND `key` = :key")
    suspend fun deleteTrigram(language: String, previous2: String, previous: String, key: String)

    @Query("DELETE FROM learned_word") suspend fun clearWords()
    @Query("DELETE FROM learned_bigram") suspend fun clearBigrams()
    @Query("DELETE FROM learned_trigram") suspend fun clearTrigrams()
    @Query("DELETE FROM avro_selection") suspend fun clearSelections()

    @Query("SELECT * FROM learned_word") suspend fun allWords(): List<LearnedWordEntity>
    @Query("SELECT * FROM learned_bigram") suspend fun allBigrams(): List<LearnedBigramEntity>
    @Query("SELECT * FROM learned_trigram") suspend fun allTrigrams(): List<LearnedTrigramEntity>
    @Query("SELECT * FROM avro_selection") suspend fun allSelections(): List<AvroSelectionEntity>
    @Query("SELECT * FROM personal_word") suspend fun allPersonal(): List<PersonalWordEntity>

    @Query("SELECT * FROM personal_word ORDER BY language, word COLLATE NOCASE")
    fun observePersonal(): Flow<List<PersonalWordEntity>>

    @Query("SELECT COUNT(*) FROM learned_word")
    fun observeLearnedCount(): Flow<Int>
}

/**
 * Everything Jolpai stores about the user's typing. It lives only on the device
 * (excluded from cloud backup, see the app's backup rules).
 */
@Database(
    entities = [
        LearnedWordEntity::class, LearnedBigramEntity::class, LearnedTrigramEntity::class, AvroSelectionEntity::class, PersonalWordEntity::class,
        ClipEntity::class, SnippetEntity::class, EmojiUsageEntity::class, CustomThemeEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class JolpaiDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun clipboard(): ClipboardDao
    abstract fun snippets(): SnippetDao
    abstract fun emoji(): EmojiDao
    abstract fun themes(): ThemeDao

    companion object {
        const val NAME = "jolpai.db"

        /**
         * Opens the database, encrypted with SQLCipher under a key held by the Android Keystore
         * ([DatabaseKey]). A database from an older Jolpai (unencrypted) is converted in place the
         * first time. If the key can no longer be read (the Keystore was reset), the unreadable
         * database is removed and a new, empty one is started, rather than crashing.
         */
        fun create(context: Context): JolpaiDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = passphrase(context)
            return Room.databaseBuilder(context, JolpaiDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }

        /** The SQLCipher passphrase: the Keystore-protected key as hex. Handles conversion and key loss. */
        private fun passphrase(context: Context): String {
            val file = context.getDatabasePath(NAME)
            File(context.filesDir, "enc-debug.txt").delete() // left by a test build
            val hadKey = DatabaseKey.exists(context)
            val key = runCatching { DatabaseKey.get(context) }.getOrElse {
                deleteDatabaseFiles(file)
                DatabaseKey.reset(context)
                DatabaseKey.get(context)
            }
            val hex = key.joinToString("") { "%02x".format(it) }
            when {
                !file.exists() -> Unit
                // If converting fails, keep using the plain database this time and try again next start.
                isPlaintext(file) -> if (runCatching { encryptInPlace(file, hex) }.isFailure) return ""
                !hadKey -> deleteDatabaseFiles(file) // encrypted with a key that no longer exists
            }
            return hex
        }

        private fun isPlaintext(file: File): Boolean = runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3" + Char(0)
            }
        }.getOrDefault(false)

        /** Copies a plain database into an encrypted one (sqlcipher_export) and swaps the files. */
        private fun encryptInPlace(file: File, hex: String) {
            val tmp = File(file.path + ".enc")
            tmp.delete()
            // CREATE: ATTACH uses this connection's open flags to create the encrypted copy.
            val db = SQLiteDatabase.openDatabase(file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null)
            try {
                val version = db.version
                // ATTACH is per connection: execSQL is how SQLite's Android wrapper runs it on the
                // one connection this plain (non-WAL) open uses; the export is a query on it.
                db.execSQL("ATTACH DATABASE '${tmp.path}' AS encrypted KEY '$hex'")
                db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA encrypted.user_version = $version")
                db.execSQL("DETACH DATABASE encrypted")
            } finally {
                db.close()
            }
            // Replace the plain file in one step, then drop its journal files.
            check(tmp.length() > 0 && tmp.renameTo(file)) { "could not replace the database" }
            listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }

        private fun deleteDatabaseFiles(file: File) {
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }
    }
}

/** Version 6: learned words and pairs fade over time (weight, and a time for pairs), and three-word sequences. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE learned_word ADD COLUMN weight REAL NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE learned_bigram ADD COLUMN lastUsed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE learned_bigram ADD COLUMN weight REAL NOT NULL DEFAULT -1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learned_trigram` (`language` TEXT NOT NULL, `previous2` TEXT NOT NULL, " +
                "`previous` TEXT NOT NULL, `key` TEXT NOT NULL, `word` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                "`lastUsed` INTEGER NOT NULL, `weight` REAL NOT NULL, PRIMARY KEY(`language`, `previous2`, `previous`, `key`))",
        )
    }
}
