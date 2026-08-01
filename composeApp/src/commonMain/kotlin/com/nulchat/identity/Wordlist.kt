package com.nulchat.identity

/**
 * IMPORTANT — ACTION REQUIRED BEFORE BUILDING:
 *
 * This must contain the official 2048-word BIP-39 English wordlist, in the
 * exact original order (order is part of the spec; do not sort or reorder).
 *
 * Get it from the canonical source:
 *   https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt
 *
 * Paste all 2048 words below, one per line inside the array, replacing the
 * placeholder entries. A handful of sample entries are included just so the
 * project has correct structure and the rest of the code compiles/typechecks.
 */
object Wordlist {
    val words: List<String> = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract"
        // ... paste the remaining 2040 words here, in official order ...
    )

    init {
        check(words.size == 2048) {
            "Wordlist incomplete: found ${words.size}/2048 words. " +
                "Paste the full official BIP-39 English list into Wordlist.kt."
        }
    }
}
