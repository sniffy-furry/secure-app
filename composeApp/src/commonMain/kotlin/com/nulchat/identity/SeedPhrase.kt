package com.nulchat.identity

import com.ionspin.kotlin.crypto.hash.Hash
import com.ionspin.kotlin.crypto.randombytes.LibsodiumRandom

/**
 * BIP-39-style 24-word recovery phrase for a 256-bit (32-byte) seed.
 *
 * NOTE: [Wordlist.words] must be populated with the official 2048-word BIP-39
 * English list before shipping. Download it from the official reference
 * (bitcoin/bips repo, bip-0039/english.txt) and paste it into Wordlist.kt,
 * one word per array entry, in the exact original order — the order is part
 * of the spec and cannot be changed or re-sorted.
 */
object SeedPhrase {

    /** Generates a fresh random 32-byte seed and its 24-word representation. */
    suspend fun generate(): Pair<ByteArray, List<String>> {
        IdentityGenerator.ensureReady()
        val entropy = LibsodiumRandom.buf(32).toByteArray()
        return entropy to toWords(entropy)
    }

    /** Converts 32 bytes of entropy into 24 words (11 bits per word + checksum). */
    fun toWords(entropy: ByteArray): List<String> {
        require(entropy.size == 32) { "Expected 32 bytes of entropy, got ${entropy.size}" }
        val checksumBits = sha256(entropy)[0].toInt() and 0xFF
        // 32 bytes = 256 bits entropy + 8 checksum bits = 264 bits = 24 * 11 bits
        val bits = StringBuilder()
        for (b in entropy) {
            bits.append(((b.toInt() and 0xFF) or 0x100).toString(2).substring(1))
        }
        bits.append(checksumBits.toString(2).padStart(8, '0'))

        val words = mutableListOf<String>()
        for (i in 0 until 24) {
            val chunk = bits.substring(i * 11, i * 11 + 11)
            val index = chunk.toInt(2)
            words.add(Wordlist.words[index])
        }
        return words
    }

    /** Recovers the original 32-byte entropy (== seed) from 24 words. Validates checksum. */
    fun toEntropy(words: List<String>): ByteArray {
        require(words.size == 24) { "Expected 24 words, got ${words.size}" }
        val bits = StringBuilder()
        for (word in words) {
            val index = Wordlist.words.indexOf(word.lowercase().trim())
            require(index >= 0) { "'$word' is not a valid recovery word" }
            bits.append(index.toString(2).padStart(11, '0'))
        }
        val entropyBits = bits.substring(0, 256)
        val checksumBits = bits.substring(256, 264)

        val entropy = ByteArray(32)
        for (i in 0 until 32) {
            entropy[i] = entropyBits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }

        val expectedChecksum = (sha256(entropy)[0].toInt() and 0xFF).toString(2).padStart(8, '0')
        require(expectedChecksum == checksumBits) { "Invalid recovery phrase (checksum mismatch)" }

        return entropy
    }

    private fun sha256(data: ByteArray): ByteArray =
        Hash.sha256(data.toUByteArray()).toByteArray()
}
