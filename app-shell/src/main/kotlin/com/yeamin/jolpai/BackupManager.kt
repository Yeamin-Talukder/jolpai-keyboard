package com.yeamin.jolpai

import com.yeamin.jolpai.clipboard.ClipboardRepository
import com.yeamin.jolpai.core.settings.KeyboardSettingsRepository
import com.yeamin.jolpai.core.suggest.SuggestionResources
import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.data.AvroSelectionEntity
import com.yeamin.jolpai.data.HistoryDao
import com.yeamin.jolpai.data.LearnedBigramEntity
import com.yeamin.jolpai.data.LearnedWordEntity
import com.yeamin.jolpai.data.RoomHistoryStore
import com.yeamin.jolpai.data.SnippetDao
import com.yeamin.jolpai.data.ThemeDao
import com.yeamin.jolpai.suggest.Language
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What a restore brought back. */
data class RestoreSummary(val settings: Int, val personalWords: Int, val snippets: Int, val themes: Int, val learnedWords: Int)

/**
 * Backup to a file the user chooses, and restore from it. Contains settings, the personal
 * dictionary, snippets and custom themes; learned words, word pairs and Avro picks only
 * when the user asks. Clipboard history, pictures and the GIPHY key are never included.
 */
@Singleton
class BackupManager @Inject constructor(
    private val settings: KeyboardSettingsRepository,
    private val historyDao: HistoryDao,
    private val snippetDao: SnippetDao,
    private val themeDao: ThemeDao,
    private val clipboard: ClipboardRepository,
    private val themes: ThemeRepository,
    private val resources: SuggestionResources,
    private val store: RoomHistoryStore,
) {
    suspend fun export(includeLearned: Boolean): String = buildJsonObject {
        put("format", FORMAT)
        put("createdAt", System.currentTimeMillis())
        put("settings", buildJsonObject {
            settings.exportAll().forEach { (k, v) ->
                when (v) {
                    is Boolean -> put(k, typed("b", JsonPrimitive(v)))
                    is Int -> put(k, typed("i", JsonPrimitive(v)))
                    is Float -> put(k, typed("f", JsonPrimitive(v)))
                    is String -> put(k, typed("s", JsonPrimitive(v)))
                    is Set<*> -> put(k, typed("set", JsonArray(v.filterIsInstance<String>().map(::JsonPrimitive))))
                }
            }
        })
        put("personal", buildJsonArray {
            historyDao.allPersonal().forEach { add(buildJsonObject { put("lang", it.language); put("word", it.word) }) }
        })
        put("snippets", buildJsonArray {
            snippetDao.all().forEach { s ->
                add(buildJsonObject { put("title", s.title); s.shortcut?.let { put("shortcut", it) }; put("text", s.text); put("category", s.category) })
            }
        })
        put("themes", buildJsonArray { themeDao.all().forEach { add(JsonPrimitive(it.json)) } })
        if (includeLearned) {
            put("learned", buildJsonObject {
                put("words", buildJsonArray {
                    historyDao.allWords().forEach { w ->
                        add(buildJsonObject { put("lang", w.language); put("word", w.word); put("count", w.count); put("lastUsed", w.lastUsed) })
                    }
                })
                put("bigrams", buildJsonArray {
                    historyDao.allBigrams().forEach { b ->
                        add(buildJsonObject { put("lang", b.language); put("prev", b.previous); put("word", b.word); put("count", b.count) })
                    }
                })
                put("avro", buildJsonObject { historyDao.allSelections().forEach { put(it.roman, it.bangla) } })
            })
        }
    }.toString()

    /** Restores a backup, adding to what is already there. Throws [IllegalArgumentException] for a wrong file. */
    suspend fun restore(json: String): RestoreSummary {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        require(root != null && root["format"]?.jsonPrimitive?.contentOrNull == FORMAT) { "That file isn't a Jolpai backup." }

        val restoredSettings = HashMap<String, Any>()
        root["settings"]?.jsonObject?.forEach { (k, e) ->
            val o = e as? JsonObject ?: return@forEach
            val v = o["v"] ?: return@forEach
            when (o["t"]?.jsonPrimitive?.contentOrNull) {
                "b" -> v.jsonPrimitive.booleanOrNull?.let { restoredSettings[k] = it }
                "i" -> v.jsonPrimitive.intOrNull?.let { restoredSettings[k] = it }
                "f" -> v.jsonPrimitive.floatOrNull?.let { restoredSettings[k] = it }
                "s" -> v.jsonPrimitive.contentOrNull?.let { restoredSettings[k] = it }
                "set" -> restoredSettings[k] = v.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            }
        }
        settings.importAll(restoredSettings)

        var personal = 0
        root["personal"]?.jsonArray?.forEach { e ->
            val o = e.jsonObject
            val lang = Language.fromStorageKey(o.str("lang") ?: return@forEach) ?: return@forEach
            resources.history.addPersonal(lang, o.str("word") ?: return@forEach)
            personal++
        }

        var snippets = 0
        val existingShortcuts = snippetDao.all().mapNotNull { it.shortcut }.toSet()
        root["snippets"]?.jsonArray?.forEach { e ->
            val o = e.jsonObject
            val text = o.str("text") ?: return@forEach
            val shortcut = o.str("shortcut")?.takeIf { it !in existingShortcuts }
            clipboard.saveSnippet(o.str("title").orEmpty(), shortcut, text, category = o.str("category")?.let(com.yeamin.jolpai.data.SnippetCategory::fromStorageKey))
            snippets++
        }

        var themeCount = 0
        root["themes"]?.jsonArray?.forEach { e ->
            if (runCatching { themes.import(e.jsonPrimitive.content) }.isSuccess) themeCount++
        }

        var learned = 0
        root["learned"]?.jsonObject?.let { l ->
            l["words"]?.jsonArray?.forEach { e ->
                val o = e.jsonObject
                val word = o.str("word") ?: return@forEach
                historyDao.upsertWord(
                    LearnedWordEntity(o.str("lang") ?: return@forEach, word.lowercase(), word, o.int("count") ?: 1, o.long("lastUsed") ?: 0),
                )
                learned++
            }
            l["bigrams"]?.jsonArray?.forEach { e ->
                val o = e.jsonObject
                val word = o.str("word") ?: return@forEach
                historyDao.upsertBigram(
                    LearnedBigramEntity(o.str("lang") ?: return@forEach, (o.str("prev") ?: return@forEach).lowercase(), word.lowercase(), word, o.int("count") ?: 1),
                )
            }
            l["avro"]?.jsonObject?.forEach { (roman, bangla) ->
                bangla.jsonPrimitive.contentOrNull?.let { historyDao.upsertSelection(AvroSelectionEntity(roman, it)) }
            }
            // The keyboard keeps learning in memory: reload it from the database.
            resources.history.load(store.loadSnapshot())
        }
        return RestoreSummary(restoredSettings.size, personal, snippets, themeCount, learned)
    }

    private fun typed(type: String, value: JsonElement) = buildJsonObject {
        put("t", type)
        put("v", value)
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private companion object {
        // "lipi" is the app's first name; kept so backups made by older versions still import.
        const val FORMAT = "lipi-backup-1"
    }
}
