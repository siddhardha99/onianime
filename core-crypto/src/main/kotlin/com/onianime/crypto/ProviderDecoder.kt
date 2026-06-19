package com.onianime.crypto

/**
 * Port of ani-cli's `provider_init` hex-substitution decoder.
 *
 * allanime returns provider source URLs obfuscated: when a sourceUrl starts with "--", the rest is
 * a stream of hex byte-pairs, each mapped to one character by the table below (lifted verbatim from
 * the big `sed` chain in ani-cli). After decoding, "/clock" is rewritten to "/clock.json".
 */
object ProviderDecoder {

    // hex pair -> character  (from ani-cli provider_init)
    private val TABLE: Map<String, String> = buildMap {
        // uppercase
        put("79", "A"); put("7a", "B"); put("7b", "C"); put("7c", "D"); put("7d", "E")
        put("7e", "F"); put("7f", "G"); put("70", "H"); put("71", "I"); put("72", "J")
        put("73", "K"); put("74", "L"); put("75", "M"); put("76", "N"); put("77", "O")
        put("68", "P"); put("69", "Q"); put("6a", "R"); put("6b", "S"); put("6c", "T")
        put("6d", "U"); put("6e", "V"); put("6f", "W"); put("60", "X"); put("61", "Y")
        put("62", "Z")
        // lowercase
        put("59", "a"); put("5a", "b"); put("5b", "c"); put("5c", "d"); put("5d", "e")
        put("5e", "f"); put("5f", "g"); put("50", "h"); put("51", "i"); put("52", "j")
        put("53", "k"); put("54", "l"); put("55", "m"); put("56", "n"); put("57", "o")
        put("48", "p"); put("49", "q"); put("4a", "r"); put("4b", "s"); put("4c", "t")
        put("4d", "u"); put("4e", "v"); put("4f", "w"); put("40", "x"); put("41", "y")
        put("42", "z")
        // digits
        put("08", "0"); put("09", "1"); put("0a", "2"); put("0b", "3"); put("0c", "4")
        put("0d", "5"); put("0e", "6"); put("0f", "7"); put("00", "8"); put("01", "9")
        // symbols
        put("15", "-"); put("16", "."); put("67", "_"); put("46", "~"); put("02", ":")
        put("17", "/"); put("07", "?"); put("1b", "#"); put("63", "["); put("65", "]")
        put("78", "@"); put("19", "!"); put("1c", "$"); put("1e", "&"); put("10", "(")
        put("11", ")"); put("12", "*"); put("13", "+"); put("14", ","); put("03", ";")
        put("05", "="); put("1d", "%")
    }

    /**
     * Decodes an obfuscated provider source URL. If [raw] does not start with "--" it is returned
     * unchanged (some providers, e.g. mp4upload, hand back plain URLs).
     */
    fun decode(raw: String): String {
        if (!raw.startsWith("--")) return raw
        val body = raw.removePrefix("--")
        val out = StringBuilder(body.length / 2)
        var i = 0
        while (i + 1 < body.length) {
            out.append(TABLE[body.substring(i, i + 2)] ?: "")
            i += 2
        }
        return out.toString().replace("/clock", "/clock.json")
    }
}
