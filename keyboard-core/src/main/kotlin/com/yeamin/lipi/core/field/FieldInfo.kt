package com.yeamin.lipi.core.field

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class FieldKind { TEXT, EMAIL, URI, NUMBER, PHONE, DATETIME, RAW }

/** What the Enter key should do in the current field. */
enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/**
 * Everything the keyboard needs to know about the focused text field, derived from
 * `EditorInfo.inputType` and `EditorInfo.imeOptions` by [FieldClassifier].
 */
data class FieldInfo(
    val kind: FieldKind = FieldKind.TEXT,
    val isPassword: Boolean = false,
    val multiline: Boolean = false,
    /** The app asked for no suggestions (e.g. a code or captcha field). */
    val noSuggestions: Boolean = false,
    /** The app asked the keyboard not to learn (incognito tabs, banking apps). */
    val noPersonalizedLearning: Boolean = false,
    val enterAction: EnterAction = EnterAction.NEWLINE,
    /** The raw EditorInfo action id to send for [enterAction] (unused for NEWLINE). */
    val editorActionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
) {
    /**
     * Sensitive fields get no suggestions, no learning, no clipboard capture and no
     * network features. See docs/ARCHITECTURE.md §8.
     */
    val isSensitive: Boolean get() = isPassword || noPersonalizedLearning

    companion object {
        val DEFAULT = FieldInfo()
    }
}

object FieldClassifier {

    fun classify(inputType: Int, imeOptions: Int): FieldInfo {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val flags = inputType and InputType.TYPE_MASK_FLAGS

        val kind: FieldKind
        var password = false
        when (cls) {
            InputType.TYPE_CLASS_TEXT -> {
                kind = when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URI
                    else -> FieldKind.TEXT
                }
                password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            }
            InputType.TYPE_CLASS_NUMBER -> {
                kind = FieldKind.NUMBER
                password = variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            InputType.TYPE_CLASS_PHONE -> kind = FieldKind.PHONE
            InputType.TYPE_CLASS_DATETIME -> kind = FieldKind.DATETIME
            else -> kind = FieldKind.RAW
        }

        val isText = cls == InputType.TYPE_CLASS_TEXT
        val multiline = isText && (flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noSuggestions = isText && (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val noLearning = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        val enter = if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            EnterAction.NEWLINE
        } else {
            when (actionId) {
                EditorInfo.IME_ACTION_GO -> EnterAction.GO
                EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
                EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
                EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
                EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
                else -> EnterAction.NEWLINE
            }
        }

        return FieldInfo(
            kind = kind,
            isPassword = password,
            multiline = multiline,
            noSuggestions = noSuggestions,
            noPersonalizedLearning = noLearning,
            enterAction = enter,
            editorActionId = actionId,
        )
    }
}
