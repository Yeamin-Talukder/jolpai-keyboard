package com.yeamin.jolpai.clipboard

import com.yeamin.jolpai.data.SnippetCategory

/** One ready-made snippet in a [SnippetPack]. */
data class PackSnippet(val title: String, val text: String, val shortcut: String, val category: SnippetCategory)

/**
 * Ready-made snippets people use every day: Islamic greetings and everyday messages, in Bangla
 * and English. Both packs are added once on their own (see [ClipboardRepository.addPacksOnce]);
 * the Snippets screen can add them again. Type a shortcut, then space, to insert the text.
 */
enum class SnippetPack(val label: String, val items: List<PackSnippet>) {
    ISLAMIC(
        "Islamic greetings",
        listOf(
            PackSnippet("আসসালামু আলাইকুম", "আসসালামু আলাইকুম", "@salam", SnippetCategory.ISLAMIC),
            PackSnippet("সালাম (পূর্ণ)", "আসসালামু আলাইকুম ওয়া রাহমাতুল্লাহি ওয়া বারাকাতুহু", "@salamf", SnippetCategory.ISLAMIC),
            PackSnippet("সালামের উত্তর", "ওয়ালাইকুম আসসালাম", "@walaikum", SnippetCategory.ISLAMIC),
            PackSnippet("সালামের উত্তর (পূর্ণ)", "ওয়ালাইকুম আসসালাম ওয়া রাহমাতুল্লাহি ওয়া বারাকাতুহু", "@walaikumf", SnippetCategory.ISLAMIC),
            PackSnippet("Assalamu Alaikum", "Assalamu Alaikum", "@as", SnippetCategory.ISLAMIC),
            PackSnippet("Wa Alaikum Assalam", "Wa Alaikum Assalam", "@ws", SnippetCategory.ISLAMIC),
            PackSnippet("বিসমিল্লাহ", "বিসমিল্লাহির রাহমানির রাহিম", "@bismillah", SnippetCategory.ISLAMIC),
            PackSnippet("আলহামদুলিল্লাহ", "আলহামদুলিল্লাহ", "@alhamd", SnippetCategory.ISLAMIC),
            PackSnippet("ইনশাআল্লাহ", "ইনশাআল্লাহ", "@insha", SnippetCategory.ISLAMIC),
            PackSnippet("মাশাআল্লাহ", "মাশাআল্লাহ", "@masha", SnippetCategory.ISLAMIC),
            PackSnippet("সুবহানাল্লাহ", "সুবহানাল্লাহ", "@subhan", SnippetCategory.ISLAMIC),
            PackSnippet("আস্তাগফিরুল্লাহ", "আস্তাগফিরুল্লাহ", "@astag", SnippetCategory.ISLAMIC),
            PackSnippet("জাযাকাল্লাহু খাইরান", "জাযাকাল্লাহু খাইরান", "@jazak", SnippetCategory.ISLAMIC),
            PackSnippet("আমিন", "আমিন", "@amin", SnippetCategory.ISLAMIC),
            PackSnippet("ইন্না লিল্লাহ", "ইন্না লিল্লাহি ওয়া ইন্না ইলাইহি রাজিউন", "@inna", SnippetCategory.ISLAMIC),
            PackSnippet("ঈদ মোবারক", "ঈদ মোবারক! আপনাকে ও আপনার পরিবারকে ঈদের শুভেচ্ছা।", "@eid", SnippetCategory.ISLAMIC),
            PackSnippet("রমজান মোবারক", "রমজান মোবারক!", "@ramadan", SnippetCategory.ISLAMIC),
            PackSnippet("জুমা মোবারক", "জুমা মোবারক!", "@jumma", SnippetCategory.ISLAMIC),
        ),
    ),
    EVERYDAY(
        "Everyday messages",
        listOf(
            PackSnippet("কেমন আছেন?", "কেমন আছেন?", "@kemon", SnippetCategory.REPLY),
            PackSnippet("ধন্যবাদ", "অনেক ধন্যবাদ!", "@dhonno", SnippetCategory.REPLY),
            PackSnippet("শুভ সকাল", "শুভ সকাল!", "@shokal", SnippetCategory.MESSAGE),
            PackSnippet("শুভ রাত্রি", "শুভ রাত্রি!", "@ratri", SnippetCategory.MESSAGE),
            PackSnippet("ব্যস্ত আছি", "এখন একটু ব্যস্ত আছি, পরে কথা বলছি।", "@bbusy", SnippetCategory.REPLY),
            PackSnippet("আসছি", "আমি আসছি, একটু অপেক্ষা করো।", "@aschi", SnippetCategory.REPLY),
            PackSnippet("পরে ফোন দিচ্ছি", "একটু পরে ফোন দিচ্ছি।", "@phone", SnippetCategory.REPLY),
            PackSnippet("শুভ জন্মদিন", "শুভ জন্মদিন! তোমার জীবন আনন্দে ভরে উঠুক। 🎂", "@jonmo", SnippetCategory.MESSAGE),
            PackSnippet("অভিনন্দন", "অভিনন্দন! 🎉", "@ovi", SnippetCategory.MESSAGE),
            PackSnippet("Thank you", "Thank you so much!", "@ty", SnippetCategory.REPLY),
            PackSnippet("Good morning", "Good morning! Have a great day.", "@gm", SnippetCategory.MESSAGE),
            PackSnippet("Good night", "Good night, sleep well.", "@gn", SnippetCategory.MESSAGE),
            PackSnippet("On my way", "I'm on my way.", "@otw", SnippetCategory.REPLY),
            PackSnippet("Busy", "I'm busy right now, I'll call you back soon.", "@busy", SnippetCategory.REPLY),
            PackSnippet("Missed call", "Sorry, I missed your call. I'll call you back soon.", "@missed", SnippetCategory.REPLY),
            PackSnippet("Happy birthday", "Happy birthday! Wishing you a wonderful year ahead. 🎂", "@hbd", SnippetCategory.MESSAGE),
            PackSnippet("Congratulations", "Congratulations! 🎉", "@congrats", SnippetCategory.MESSAGE),
            PackSnippet("Get well soon", "Get well soon! Take care.", "@gws", SnippetCategory.MESSAGE),
        ),
    ),
}
