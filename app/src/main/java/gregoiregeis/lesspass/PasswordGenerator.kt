package gregoiregeis.lesspass

import android.text.style.ForegroundColorSpan
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA384Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.jetbrains.anko.append
import org.jetbrains.anko.buildSpanned
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val byteToHex = (
        "000102030405060708090A0B0C0D0E0F" +
        "101112131415161718191A1B1C1D1E1F" +
        "202122232425262728292A2B2C2D2E2F" +
        "303132333435363738393A3B3C3D3E3F" +
        "404142434445464748494A4B4C4D4E4F" +
        "505152535455565758595A5B5C5D5E5F" +
        "606162636465666768696A6B6C6D6E6F" +
        "707172737475767778797A7B7C7D7E7F" +
        "808182838485868788898A8B8C8D8E8F" +
        "909192939495969798999A9B9C9D9E9F" +
        "A0A1A2A3A4A5A6A7A8A9AAABACADAEAF" +
        "B0B1B2B3B4B5B6B7B8B9BABBBCBDBEBF" +
        "C0C1C2C3C4C5C6C7C8C9CACBCCCDCECF" +
        "D0D1D2D3D4D5D6D7D8D9DADBDCDDDEDF" +
        "E0E1E2E3E4E5E6E7E8E9EAEBECEDEEEF" +
        "F0F1F2F3F4F5F6F7F8F9FAFBFCFDFEFF").toCharArray()

// See: https://stackoverflow.com/a/21429909
private fun ByteArray.toHexString(): String {
    val len = size
    val chars = CharArray(len shl 1)
    var hexIndex: Int
    var idx = 0
    var ofs = 0

    while (ofs < len) {
        hexIndex = this[ofs++].toInt() and 0xFF shl 1
        chars[idx++] = byteToHex[hexIndex++]
        chars[idx++] = byteToHex[hexIndex]
    }

    return String(chars)
}

private fun BigInteger.divRem(div: BigInteger): Pair<BigInteger, Int> {
    val res = this.divideAndRemainder(div)

    return Pair(res[0], res[1].toInt())
}

fun generateEntropy(password: String, salt: String, settings: Settings): String {
    val digest: Digest = when (settings.algo) {
        Algorithm.SHA256 -> SHA256Digest()
        Algorithm.SHA384 -> SHA384Digest()
        Algorithm.SHA512 -> SHA512Digest()
    }

    // Key generation
    val saltBuffer = salt.toByteArray(Charsets.UTF_8)

    val generator = PKCS5S2ParametersGenerator(digest).apply {
        init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password.toCharArray()), saltBuffer, settings.iterations)
    }

    val keyParam = generator.generateDerivedMacParameters(32 * 8) as KeyParameter
    val key = keyParam.key

    // Byte buffer -> String conversion
    return key.toHexString()
}

fun renderPassword(settings: Settings, entropy: String): String {
    var quotient = BigInteger(entropy, 16)
    val (possibleChars, allSets) = CharacterSet.getCharacterSet(settings.charSets)

    val setsCount = allSets.size
    val maxLen = settings.length - setsCount

    val passwordChars = CharArray(maxLen)
    val charsLen = possibleChars.size.toBigInteger()

    for (i in 0 until maxLen) {
        val (quot, rem) = quotient.divRem(charsLen)

        passwordChars[i] = possibleChars[rem]
        quotient = quot
    }

    // Add one char per rule
    val additionalChars = CharArray(setsCount)

    for (i in 0 until setsCount) {
        val set = allSets[i]
        val (quot, rem) = quotient.divRem(set.size.toBigInteger())

        additionalChars[i] = set[rem]
        quotient = quot
    }

    // Randomly distribute rule-chars into password-chars
    var password = String(passwordChars)

    for (i in 0 until setsCount) {
        val (quot, rem) = quotient.divRem(password.length.toBigInteger())

        password = "${password.substring(0, rem)}${additionalChars[i]}${password.substring(rem)}"
        quotient = quot
    }

    return password
}


// Fingerprint, see https://github.com/lesspass/lesspass/blob/master/master-password/src/index.js

fun generateFingerprintHash(password: String): ByteArray {
    val key = password.toByteArray(Charsets.UTF_8)
    val hasher = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(key, "HmacSHA256"))
    }

    return hasher.doFinal(ByteArray(0))
}

private val fingerprintColors = arrayOf(
        0xFF000000.toInt(),
        0xFF074750.toInt(),
        0xFF009191.toInt(),
        0xFFFF6CB6.toInt(),
        0xFFFFB5DA.toInt(),
        0xFF490092.toInt(),
        0xFF006CDB.toInt(),
        0xFFB66DFF.toInt(),
        0xFF6DB5FE.toInt(),
        0xFFB5DAFE.toInt(),
        0xFF920000.toInt(),
        0xFF924900.toInt(),
        0xFFDB6D00.toInt(),
        0xFF24FE23.toInt()
)

private val fingerprintIcons = arrayOf(
        "\uf292",
        "\uf004",
        "\uf594",
        "\uf19c",
        "\uf1e6",
        "\uf0f9",
        "\uf207",
        "\uf1b9",
        "\uf072",
        "\uf135",
        "\uf21a",
        "\uf239",
        "\uf0d1",
        "\uf157",
        "\uf153",
        "\uf15a",
        "\uf155",
        "\uf154",
        "\uf187",
        "\uf1fe",
        "\uf236",
        "\uf0fc",
        "\uf0f3",
        "\uf1e5",
        "\uf1fd",
        "\uf1e2",
        "\uf0b1",
        "\uf188",
        "\uf030",
        "\uf217",
        "\uf0a3",
        "\uf0f4",
        "\uf0c2",
        "\uf0f4", // Repeated?
        "\uf075",
        "\uf1b2",
        "\uf2e7",
        "\uf1c0",
        "\uf219",
        "\uf06a",
        "\uf06e",
        "\uf024",
        "\uf0c3",
        "\uf1e3",
        "\uf11b",
        "\uf19d"
)

private fun ByteArray.getInt3(i: Int) =
        (0xff and this[i + 0].toInt() shl 16) or
        (0xff and this[i + 1].toInt() shl 8) or
        (0xff and this[i + 2].toInt())

fun getFingerprintSpan(fingerprint: ByteArray) = buildSpanned {
    val hash1 = fingerprint.getInt3(0)
    val hash2 = fingerprint.getInt3(3)
    val hash3 = fingerprint.getInt3(6)

    append(fingerprintIcons[hash1 % fingerprintIcons.size],
           ForegroundColorSpan(fingerprintColors[hash1 % fingerprintColors.size]))

    append(fingerprintIcons[hash2 % fingerprintIcons.size],
           ForegroundColorSpan(fingerprintColors[hash2 % fingerprintColors.size]))

    append(fingerprintIcons[hash3 % fingerprintIcons.size],
           ForegroundColorSpan(fingerprintColors[hash3 % fingerprintColors.size]))
}
