package com.yeamin.lipi.data

import android.content.Context
import com.yeamin.lipi.suggest.HistorySnapshot
import com.yeamin.lipi.suggest.HistoryStore
import com.yeamin.lipi.suggest.Language
import com.yeamin.lipi.suggest.LearnedBigram
import com.yeamin.lipi.suggest.LearnedWord
import com.yeamin.lipi.suggest.PersonalWord
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Saves learning to Room without ever blocking typing: every change is queued and written
 * in order on a background thread. Writes stay in the order they were made, so a
 * "clear" is never overtaken by an older update.
 */
@Singleton
class RoomHistoryStore @Inject constructor(private val dao: HistoryDao) : HistoryStore {

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (write in writes) {
                // A failed write loses one learning update; it must never crash the keyboard.
                runCatching { write() }
            }
        }
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    override fun saveWord(word: LearnedWord) = enqueue {
        dao.upsertWord(LearnedWordEntity(word.language.storageKey, word.word.lowercase(), word.word, word.count, word.lastUsed))
    }

    override fun saveBigram(bigram: LearnedBigram) = enqueue {
        dao.upsertBigram(
            LearnedBigramEntity(bigram.language.storageKey, bigram.previous.lowercase(), bigram.word.lowercase(), bigram.word, bigram.count),
        )
    }

    override fun saveSelection(roman: String, bangla: String) = enqueue {
        dao.upsertSelection(AvroSelectionEntity(roman, bangla))
    }

    override fun savePersonal(word: PersonalWord, present: Boolean) = enqueue {
        val key = word.word.lowercase()
        if (present) {
            dao.upsertPersonal(PersonalWordEntity(word.language.storageKey, key, word.word, System.currentTimeMillis()))
        } else {
            dao.deletePersonal(word.language.storageKey, key)
        }
    }

    override fun deleteLearned(language: Language, word: String) = enqueue {
        dao.deleteWord(language.storageKey, word.lowercase())
        dao.deleteBigramsTo(language.storageKey, word.lowercase())
    }

    override fun clearLearned() = enqueue {
        dao.clearWords()
        dao.clearBigrams()
        dao.clearSelections()
    }

    /** Reads everything stored, for [com.yeamin.lipi.suggest.UserHistory.load]. Call off the main thread. */
    suspend fun loadSnapshot(): HistorySnapshot = HistorySnapshot(
        words = dao.allWords().mapNotNull { e ->
            Language.fromStorageKey(e.language)?.let { LearnedWord(it, e.word, e.count, e.lastUsed) }
        },
        bigrams = dao.allBigrams().mapNotNull { e ->
            Language.fromStorageKey(e.language)?.let { LearnedBigram(it, e.previous, e.word, e.count) }
        },
        selections = dao.allSelections().associate { it.roman to it.bangla },
        personal = dao.allPersonal().mapNotNull { e ->
            Language.fromStorageKey(e.language)?.let { PersonalWord(it, e.word) }
        },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LipiDatabase = LipiDatabase.create(context)

    @Provides
    fun provideHistoryDao(db: LipiDatabase): HistoryDao = db.history()

    @Provides
    fun provideClipboardDao(db: LipiDatabase): ClipboardDao = db.clipboard()

    @Provides
    fun provideSnippetDao(db: LipiDatabase): SnippetDao = db.snippets()

    @Provides
    fun provideEmojiDao(db: LipiDatabase): EmojiDao = db.emoji()

    @Provides
    fun provideThemeDao(db: LipiDatabase): ThemeDao = db.themes()
}
