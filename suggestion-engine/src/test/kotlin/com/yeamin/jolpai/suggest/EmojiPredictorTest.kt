package com.yeamin.jolpai.suggest

import com.yeamin.jolpai.emoji.EmojiData
import com.yeamin.jolpai.emoji.EmojiGroup
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPredictorTest {

    companion object {
        val predictor: EmojiPredictor by lazy {
            val data = EmojiData.load()
            val usable = data.all.filter { it.group != EmojiGroup.FLAGS }.map { it.value }.toSet()
            EmojiPredictor(NextWordModelTest.model, data.englishKeywords.filterKeys { it in usable }.toList())
        }
    }

    private fun emoji(sentence: String) = predictor.predict(sentence.split(" "), limit = 3)

    @Test fun `common words come first in the model's word list`() {
        val first = NextWordModelTest.model.words.drop(3).take(12)
        println("first words: $first")
        assertTrue("the" in first && "and" in first)
    }

    @Test fun `print predictions for everyday sentences`() {
        listOf(
            "i am so exhausted", "good night everyone", "we won the final", "happy birthday my friend",
            "i love you so much", "lets get some food", "that was hilarious", "thank you so much",
            "it is raining outside", "i miss you", "congratulations on the new job", "see you at the beach",
            "my dog is sick", "coffee time", "the exam was really hard", "i am going to the gym",
            "can you call me", "what time is the meeting", "the car broke down", "ok",
        ).forEach { println("EMOJI '$it' -> ${emoji(it)}") }
    }

    @Test fun `clear sentences get a fitting emoji`() {
        assertTrue(emoji("happy birthday my friend").any { it in listOf("🎂", "🎉", "🥳", "🎁", "🎈") })
        assertTrue(emoji("good night everyone").any { it in listOf("😴", "🌙", "🌃", "💤", "🛌", "🌛", "🌜") })
        assertTrue(emoji("i love you so much").any { it in listOf("❤️", "🥰", "😍", "😘", "💕") })
        assertTrue(emoji("coffee time").first() == "☕")
    }

    @Test fun `plain words give nothing`() {
        assertTrue(emoji("what is the").isEmpty())
        assertTrue(emoji("is it there or not").isEmpty())
    }
}
