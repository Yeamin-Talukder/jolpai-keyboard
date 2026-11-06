package com.yeamin.lipi.bangla

/** Character classes for the Bangla (Bengali) Unicode block, U+0980–U+09FF. */
object BanglaChars {
    const val HASANTA = '্'
    const val ZWNJ = '‌'
    const val ZWJ = '‍'

    /** Dependent vowel signs (kar), including the au length mark ৗ. */
    private const val KARS = "ািীুূৃৄেৈোৌৗ"

    fun isBangla(c: Char): Boolean = c in 'ঀ'..'৿'

    fun isKar(c: Char): Boolean = KARS.indexOf(c) >= 0

    /** Consonants ক–হ, plus ড় ঢ় য় and khanda ta ৎ. */
    fun isConsonant(c: Char): Boolean =
        c in 'ক'..'হ' || c == 'ড়' || c == 'ঢ়' || c == 'য়' || c == 'ৎ'

    /** Independent vowels অ–ঔ, plus ৠ ঌ ৡ. */
    fun isIndependentVowel(c: Char): Boolean = c in 'অ'..'ঔ' || c == 'ৠ' || c == 'ৡ'
}
