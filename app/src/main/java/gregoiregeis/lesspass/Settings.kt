package gregoiregeis.lesspass

import android.content.Context
import android.database.CharArrayBuffer
import java.io.CharArrayWriter

inline fun Int.hasFlag(flag: Int) = (this and flag) == flag

enum class Algorithm(val bits: Int) {
    SHA256(256),
    SHA384(384),
    SHA512(512);

    companion object {
        fun fromBits(bits: Int) = when (bits) {
            256 -> SHA256
            384 -> SHA384
            512 -> SHA512

            else -> error("Invalid algorithm bits.")
        }
    }
}

class CharacterSet {
    companion object {
        const val Uppercase = 1
        const val Lowercase = 2
        const val Numbers   = 4
        const val Symbols   = 8

        const val Letters   = Uppercase or Lowercase
        const val All       = Letters or Numbers or Symbols


        // Adapted from https://github.com/6A/LessPassForWindows/blob/master/LessPass/Generator.cs

        private val emptyCharArray = CharArray(0)

        private const val lowercaseLetters = "abcdefghijklmnopqrstuvwxyz"
        private const val uppercaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val symbols = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        private const val numbers = "0123456789"

        fun getCharacterSet(charSets: Int): Pair<CharArray, Array<CharArray>> {
            var len = 0
            var allSetsLen = 0

            if (charSets.hasFlag(CharacterSet.Lowercase)) {
                len += 26
                allSetsLen++
            }
            if (charSets.hasFlag(CharacterSet.Uppercase)) {
                len += 26
                allSetsLen++
            }
            if (charSets.hasFlag(CharacterSet.Numbers)) {
                len += 10
                allSetsLen++
            }
            if (charSets.hasFlag(CharacterSet.Symbols)) {
                len += 32
                allSetsLen++
            }

            var index = 0
            var setIndex = 0

            val chars = CharArray(len)
            val charGroups = Array(allSetsLen) { emptyCharArray }

            if (charSets.hasFlag(CharacterSet.Lowercase)) {
                lowercaseLetters.toCharArray(chars, 0, 0)
                index += 26

                charGroups[setIndex++] = lowercaseLetters.toCharArray()
            }
            if (charSets.hasFlag(CharacterSet.Uppercase)) {
                uppercaseLetters.toCharArray(chars, index, 0)
                index += 26

                charGroups[setIndex++] = uppercaseLetters.toCharArray()
            }
            if (charSets.hasFlag(CharacterSet.Numbers)) {
                numbers.toCharArray(chars, index, 0)
                index += 10

                charGroups[setIndex++] = numbers.toCharArray()
            }
            if (charSets.hasFlag(CharacterSet.Symbols)) {
                symbols.toCharArray(chars, index, 0)
                index += 32

                charGroups[setIndex] = symbols.toCharArray()
            }

            return Pair(chars, charGroups)
        }
    }
}

private const val SettingsPreferencesKey = "SETTINGS"

data class Settings(var length: Int, var counter: Int, var iterations: Int, var charSets: Int, var algo: Algorithm) {
    companion object {
        fun load(ctx: Context): Settings {
            val prefs = ctx.getSharedPreferences(SettingsPreferencesKey, Context.MODE_PRIVATE)

            return Settings(
                    prefs.getInt("length", 16),
                    prefs.getInt("counter", 1),
                    prefs.getInt("iterations", 100_000),
                    prefs.getInt("charsets", CharacterSet.All),

                    Algorithm.fromBits(prefs.getInt("algorithm", 256))
            )
        }
    }

    fun generatePassword(master: String, website: String, username: String): String {
        val salt = "$website$username${this.counter.toString(16)}"

        return renderPassword(this, generateEntropy(master, salt, this))
    }

    fun generatePreview(ctx: Context): String {
        val acceptedChars = StringBuilder(4)

        if (charSets.hasFlag(CharacterSet.Uppercase)) acceptedChars.append('A')
        if (charSets.hasFlag(CharacterSet.Lowercase)) acceptedChars.append('a')
        if (charSets.hasFlag(CharacterSet.Numbers))   acceptedChars.append('1')
        if (charSets.hasFlag(CharacterSet.Symbols))   acceptedChars.append('$')

        return ctx.getString(R.string.preview_format).format(acceptedChars, length, counter, iterations.toDouble(), algo)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(SettingsPreferencesKey, Context.MODE_PRIVATE).edit().apply {
            putInt("length", length)
            putInt("counter", counter)
            putInt("iterations", iterations)
            putInt("charsets", charSets)
            putInt("algorithm", algo.bits)

            apply()
        }
    }
}
