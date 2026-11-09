package com.yeamin.jolpai.core.suggest

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import com.yeamin.jolpai.data.RoomHistoryStore
import com.yeamin.jolpai.suggest.AvroDictionarySearch
import com.yeamin.jolpai.suggest.AvroSuggester
import com.yeamin.jolpai.suggest.BanglaDictionary
import com.yeamin.jolpai.suggest.EnglishDictionary
import com.yeamin.jolpai.suggest.NextWordModel
import com.yeamin.jolpai.suggest.Suggester
import com.yeamin.jolpai.suggest.UserHistory
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One shared copy of the dictionaries and the learned history for the whole app process,
 * used by both the keyboard service and the settings screens.
 *
 * Dictionaries load once, in the background ([loadAll]); until each one is ready its
 * suggestions are simply empty.
 */
@Singleton
class SuggestionResources @Inject constructor(private val store: RoomHistoryStore) {

    val history = UserHistory(store)

    /** The Avro rule engine, shared with the Avro layout's conversion. */
    val phonetic: Lazy<PhoneticEngine> = lazy { PhoneticEngine(AvroGrammar.load()) }

    @Volatile private var english: EnglishDictionary? = null
    @Volatile private var bangla: BanglaDictionary? = null
    @Volatile private var avro: AvroSuggester? = null

    /** The English next-word model (4.7 MB, loaded with the dictionaries); null until then or if damaged. */
    @Volatile var englishModel: NextWordModel? = null
        private set

    /** The Bangla next-word model (Avro, জাতীয়, প্রভাত); null until loaded or if missing. */
    @Volatile var banglaModel: NextWordModel? = null
        private set

    val suggester = Suggester({ english }, { bangla }, { avro }, history, model = { englishModel }, banglaModel = { banglaModel })

    private val started = AtomicBoolean(false)

    /**
     * Loads history and dictionaries. Blocking: call from a background thread. Runs once;
     * later calls return immediately. [onProgress] runs after each part is ready (on the
     * calling thread), so the keyboard can refresh its suggestions.
     */
    suspend fun loadAll(onProgress: () -> Unit) {
        if (!started.compareAndSet(false, true)) return
        runCatching { history.load(store.loadSnapshot()) }
        onProgress()
        english = EnglishDictionary.load()
        onProgress()
        englishModel = NextWordModel.loadEnglish()
        onProgress()
        banglaModel = NextWordModel.loadBangla()
        onProgress()
        val b = BanglaDictionary.load()
        bangla = b
        onProgress()
        avro = AvroSuggester.load(phonetic.value, AvroDictionarySearch.load(b), b::frequency)
        onProgress()
    }
}
