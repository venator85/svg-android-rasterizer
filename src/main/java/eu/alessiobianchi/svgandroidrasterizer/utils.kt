package eu.alessiobianchi.svgandroidrasterizer

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

val Path.name: String
    get() = fileName.toString()

fun Path.readText(charset: Charset = StandardCharsets.UTF_8): String = Files.readAllBytes(this).toString(charset)

fun Path.abs(): String = toAbsolutePath().normalize().toString()

fun Path.sha256(): String {
    val bytes = Files.readAllBytes(this)
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.toHex()
}

fun ByteArray.toHex(): String {
    val hexChars = "0123456789abcdef".toCharArray()
    val result = StringBuilder()
    for (element in this) {
        val octet = element.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(hexChars[firstIndex])
        result.append(hexChars[secondIndex])
    }
    return result.toString()
}

fun getAndroidValidName(name: String): String {
    return name.toLowerCase().replace(Regex("[^a-z0-9_]"), "_")
}

fun px(dp: Int, density: Int): Int = (dp * density / 160.0).toInt()