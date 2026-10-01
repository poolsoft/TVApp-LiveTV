package com.tvapp.livetv.ui

import java.text.Collator
import java.util.Locale

data class AlphabetTarget(val letter: String, val position: Int)

object AlphabetJump {
    private val locale = Locale.forLanguageTag("tr")

    fun letter(name: String): String {
        val text = name.trimStart()
        if (text.isEmpty()) return "#"
        val first = text.codePointAt(0)
        return if (Character.isLetter(first)) String(Character.toChars(first)).uppercase(locale) else "#"
    }

    fun targets(namesAndPositions: Iterable<Pair<String, Int>>): List<AlphabetTarget> {
        val firstPositions = mutableMapOf<String, Int>()
        for ((name, position) in namesAndPositions) {
            val letter = letter(name)
            firstPositions[letter] = minOf(position, firstPositions[letter] ?: position)
        }
        val collator = Collator.getInstance(locale)
        return firstPositions.map { AlphabetTarget(it.key, it.value) }.sortedWith { a, b ->
            when {
                a.letter == b.letter -> 0
                a.letter == "#" -> 1
                b.letter == "#" -> -1
                else -> collator.compare(a.letter, b.letter)
            }
        }
    }
}
