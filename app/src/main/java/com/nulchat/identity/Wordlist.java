package com.nulchat.identity;

/**
 * IMPORTANT — ACTION REQUIRED BEFORE BUILDING:
 *
 * WORDS must contain the official 2048-word BIP-39 English wordlist, in the
 * exact original order (order is part of the spec; do not sort or reorder).
 *
 * Get it from the canonical source:
 *   https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt
 *
 * Paste all 2048 words below, one per array entry, replacing the placeholder
 * entries. The app deliberately crashes on startup via the check in the
 * static initializer until this is done.
 */
public final class Wordlist {
    private Wordlist() {}

    public static final String[] WORDS = new String[] {
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract"
            // ... paste the remaining 2040 words here, in official order ...
    };

    static {
        if (WORDS.length != 2048) {
            throw new IllegalStateException(
                    "Wordlist incomplete: found " + WORDS.length + "/2048 words. " +
                    "Paste the full official BIP-39 English list into Wordlist.java.");
        }
    }
}
