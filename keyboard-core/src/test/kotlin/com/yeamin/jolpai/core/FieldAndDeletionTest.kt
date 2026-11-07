package com.yeamin.jolpai.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.yeamin.jolpai.core.editor.Deletion
import com.yeamin.jolpai.core.field.EnterAction
import com.yeamin.jolpai.core.field.FieldClassifier
import com.yeamin.jolpai.core.field.FieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldClassifierTest {

    private val text = InputType.TYPE_CLASS_TEXT

    @Test fun `password variants are sensitive`() {
        listOf(
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        ).forEach {
            val f = FieldClassifier.classify(it, 0)
            assertTrue(f.isPassword)
            assertTrue(f.isSensitive)
        }
    }

    @Test fun `numeric password still uses the number pad`() =
        assertEquals(FieldKind.NUMBER, FieldClassifier.classify(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0).kind)

    @Test fun `no personalised learning flag is sensitive`() {
        val f = FieldClassifier.classify(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertFalse(f.isPassword)
        assertTrue(f.isSensitive)
    }

    @Test fun `plain text is not sensitive`() = assertFalse(FieldClassifier.classify(text, 0).isSensitive)

    @Test fun `email and uri`() {
        assertEquals(FieldKind.EMAIL, FieldClassifier.classify(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0).kind)
        assertEquals(FieldKind.URI, FieldClassifier.classify(text or InputType.TYPE_TEXT_VARIATION_URI, 0).kind)
    }

    @Test fun `classes`() {
        assertEquals(FieldKind.PHONE, FieldClassifier.classify(InputType.TYPE_CLASS_PHONE, 0).kind)
        assertEquals(FieldKind.DATETIME, FieldClassifier.classify(InputType.TYPE_CLASS_DATETIME, 0).kind)
        assertEquals(FieldKind.RAW, FieldClassifier.classify(InputType.TYPE_NULL, 0).kind)
    }

    @Test fun `multiline and no suggestions flags`() {
        val f = FieldClassifier.classify(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0)
        assertTrue(f.multiline)
        assertTrue(f.noSuggestions)
    }

    @Test fun `enter action comes from ime options`() {
        assertEquals(EnterAction.SEARCH, FieldClassifier.classify(text, EditorInfo.IME_ACTION_SEARCH).enterAction)
        assertEquals(EnterAction.SEND, FieldClassifier.classify(text, EditorInfo.IME_ACTION_SEND).enterAction)
        assertEquals(EnterAction.NEWLINE, FieldClassifier.classify(text, EditorInfo.IME_ACTION_UNSPECIFIED).enterAction)
    }

    @Test fun `no enter action flag forces newline`() =
        assertEquals(
            EnterAction.NEWLINE,
            FieldClassifier.classify(text, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION).enterAction,
        )
}

class DeletionTest {

    @Test fun `empty`() = assertEquals(0, Deletion.lengthToDelete(""))

    @Test fun `latin letter`() = assertEquals(1, Deletion.lengthToDelete("ab"))

    @Test fun `bangla deletes the kar only`() = assertEquals(1, Deletion.lengthToDelete("কি"))

    @Test fun `bangla conjunct deletes one code point`() = assertEquals(1, Deletion.lengthToDelete("ক্ষ"))

    @Test fun `explicit hasanta goes in one press`() = assertEquals(2, Deletion.lengthToDelete("ক্‌"))

    @Test fun `surrogate pair emoji`() = assertEquals(2, Deletion.lengthToDelete("hi😀"))

    @Test fun `zwj family emoji goes in one press`() {
        val family = "👨‍👩‍👧"
        assertEquals(family.length, Deletion.lengthToDelete("a$family"))
    }

    @Test fun `combining accent goes with its letter`() = assertEquals(2, Deletion.lengthToDelete("é"))
}
