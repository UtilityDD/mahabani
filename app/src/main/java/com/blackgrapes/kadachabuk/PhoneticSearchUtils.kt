package com.blackgrapes.kadachabuk

import java.util.Locale

object PhoneticSearchUtils {

    /**
     * Transliterates Bengali or Hindi text into a simplified English phonetic string.
     * This is used for "fuzzy" matching when a user types in English for a regional title.
     */
    fun transliterate(text: String): String {
        val input = text.lowercase(Locale.ROOT)
        val sb = StringBuilder()

        for (char in input) {
            val mapped = mapChar(char)
            if (mapped.isNotEmpty()) {
                sb.append(mapped)
            } else if (char in 'a'..'z' || char in '0'..'9' || char.isWhitespace()) {
                sb.append(char)
            }
        }

        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun mapChar(c: Char): String {
        return when (c) {
            // --- Bengali & Hindi Vowels ---
            'অ', 'अ' -> "a"
            'আ', 'आ', 'া' -> "a"
            'ই', 'इ', 'ি' -> "i"
            'ঈ', 'ई', 'ী' -> "i"
            'উ', 'उ', 'ু' -> "u"
            'ঊ', 'ऊ', 'ূ' -> "u"
            'ঋ', 'ऋ', 'ৃ' -> "ri"
            'এ', 'ए', 'ে' -> "e"
            'ঐ', 'ऐ', 'ৈ' -> "oi"
            'ও', 'ओ', 'ো' -> "o"
            'ঔ', 'औ', 'ৌ' -> "ou"

            // --- Bengali & Hindi Consonants ---
            'ক', 'क' -> "k"
            'খ', 'ख' -> "kh"
            'গ', 'ग' -> "g"
            'ঘ', 'घ' -> "gh"
            'ঙ', 'ङ' -> "ng"
            'চ', 'च' -> "ch"
            'ছ', 'छ' -> "chh"
            'জ', 'ज' -> "j"
            'ঝ', 'झ' -> "jh"
            'ঞ', 'ञ' -> "n"
            'ট', 'ट' -> "t"
            'ঠ', 'ठ' -> "th"
            'ড', 'ड' -> "d"
            'ঢ', 'ढ' -> "dh"
            'ণ', 'ण' -> "n"
            'ত', 'त' -> "t"
            'থ', 'थ' -> "th"
            'দ', 'द' -> "d"
            'ধ', 'ध' -> "dh"
            'ন', 'न' -> "n"
            'প', 'प' -> "p"
            'ফ', 'फ' -> "ph"
            'ব', 'ब' -> "b"
            'ভ', 'भ' -> "bh"
            'ম', 'म' -> "m"
            'য', 'य' -> "j"
            'র', 'र', 'ড়', 'ड़' -> "r"
            'ল', 'ल' -> "l"
            'শ', 'श' -> "sh"
            'ষ', 'ष' -> "sh"
            'স', 'स' -> "s"
            'হ', 'ह' -> "h"
            'ঢ়', 'ढ़' -> "rh"
            'য়', 'य' -> "y"
            'ৎ' -> "t"
            'ং', 'ं' -> "ng"
            'ঃ', 'ः' -> "h"
            'ঁ', 'ँ' -> "n"

            else -> ""
        }
    }
}
