package com.yeamin.jolpai.bangla.phonetic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads the Avro Phonetic grammar bundled at `/avro/AvroPhonetic.json`
 * (MPL-2.0, from OpenBangla's rupantor — see `avro/NOTICE.txt` next to it).
 */
object AvroGrammar {

    private const val RESOURCE = "/avro/AvroPhonetic.json"

    /** Parses the bundled grammar. Takes a few milliseconds; call it off the main thread. */
    fun load(): PhoneticGrammar {
        val stream = AvroGrammar::class.java.getResourceAsStream(RESOURCE)
            ?: error("Avro grammar $RESOURCE is missing from the build")
        val text = stream.use { it.readBytes().decodeToString() }
        return parse(text)
    }

    /** Parses grammar JSON in the Avro / rupantor format. */
    fun parse(json: String): PhoneticGrammar {
        val root = Json.parseToJsonElement(json).jsonObject
        return PhoneticGrammar(
            patterns = root.getValue("patterns").jsonArray.map { parsePattern(it.jsonObject) },
            vowels = root.string("vowel"),
            consonants = root.string("consonant"),
            caseSensitive = root.string("casesensitive"),
            numbers = root["number"]?.jsonPrimitive?.content ?: "1234567890",
        )
    }

    private fun parsePattern(o: JsonObject) = PhoneticPattern(
        find = o.string("find"),
        replace = o.string("replace"),
        rules = o["rules"]?.jsonArray.orEmpty().map { r ->
            val rule = r.jsonObject
            PhoneticRule(
                matches = rule.getValue("matches").jsonArray.map { parseMatch(it.jsonObject) },
                replace = rule.string("replace"),
            )
        },
    )

    private fun parseMatch(o: JsonObject): PhoneticMatch {
        val side = when (val t = o.string("type")) {
            "prefix" -> PhoneticMatch.Side.PREFIX
            "suffix" -> PhoneticMatch.Side.SUFFIX
            else -> error("Unknown match type '$t'")
        }
        val rawScope = o.string("scope")
        val negative = rawScope.startsWith("!")
        val scope = when (val s = rawScope.removePrefix("!")) {
            "vowel" -> PhoneticMatch.Scope.VOWEL
            "consonant" -> PhoneticMatch.Scope.CONSONANT
            "punctuation" -> PhoneticMatch.Scope.PUNCTUATION
            "number" -> PhoneticMatch.Scope.NUMBER
            "exact" -> PhoneticMatch.Scope.EXACT
            else -> error("Unknown match scope '$s'")
        }
        return PhoneticMatch(side, scope, negative, o["value"]?.jsonPrimitive?.content.orEmpty())
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
