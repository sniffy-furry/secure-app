package com.nulchat.identity;

import com.nulchat.crypto.CryptoPrimitives;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * BIP-39-style 24-word recovery phrase for a 256-bit (32-byte) seed.
 *
 * NOTE: {@link Wordlist#WORDS} must be populated with the official 2048-word
 * BIP-39 English list before shipping — see Wordlist.java.
 */
public final class SeedPhrase {

    private SeedPhrase() {}

    /** Generates a fresh random 32-byte seed and its 24-word representation. */
    public static Seed generate() {
        byte[] entropy = CryptoPrimitives.randomBytes(32);
        return new Seed(entropy, toWords(entropy));
    }

    public static final class Seed {
        public final byte[] entropy;
        public final List<String> words;

        public Seed(byte[] entropy, List<String> words) {
            this.entropy = entropy;
            this.words = words;
        }
    }

    /** Converts 32 bytes of entropy into 24 words (11 bits per word + checksum). */
    public static List<String> toWords(byte[] entropy) {
        if (entropy.length != 32) {
            throw new IllegalArgumentException("Expected 32 bytes of entropy, got " + entropy.length);
        }
        int checksumByte = sha256(entropy)[0] & 0xFF;

        StringBuilder bits = new StringBuilder();
        for (byte b : entropy) {
            String byteBits = Integer.toBinaryString((b & 0xFF) | 0x100).substring(1);
            bits.append(byteBits);
        }
        bits.append(String.format("%8s", Integer.toBinaryString(checksumByte)).replace(' ', '0'));

        List<String> words = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            String chunk = bits.substring(i * 11, i * 11 + 11);
            int index = Integer.parseInt(chunk, 2);
            words.add(Wordlist.WORDS[index]);
        }
        return words;
    }

    /** Recovers the original 32-byte entropy (== seed) from 24 words. Validates checksum. */
    public static byte[] toEntropy(List<String> words) {
        if (words.size() != 24) {
            throw new IllegalArgumentException("Expected 24 words, got " + words.size());
        }
        StringBuilder bits = new StringBuilder();
        for (String word : words) {
            int index = indexOf(word.trim().toLowerCase());
            if (index < 0) {
                throw new IllegalArgumentException("'" + word + "' is not a valid recovery word");
            }
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'));
        }
        String entropyBits = bits.substring(0, 256);
        String checksumBits = bits.substring(256, 264);

        byte[] entropy = new byte[32];
        for (int i = 0; i < 32; i++) {
            entropy[i] = (byte) Integer.parseInt(entropyBits.substring(i * 8, i * 8 + 8), 2);
        }

        String expectedChecksum = String.format("%8s", Integer.toBinaryString(sha256(entropy)[0] & 0xFF)).replace(' ', '0');
        if (!expectedChecksum.equals(checksumBits)) {
            throw new IllegalArgumentException("Invalid recovery phrase (checksum mismatch)");
        }
        return entropy;
    }

    private static int indexOf(String word) {
        for (int i = 0; i < Wordlist.WORDS.length; i++) {
            if (Wordlist.WORDS[i].equals(word)) return i;
        }
        return -1;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
