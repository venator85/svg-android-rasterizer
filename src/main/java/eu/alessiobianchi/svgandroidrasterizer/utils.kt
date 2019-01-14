package eu.alessiobianchi.svgandroidrasterizer

import java.nio.file.Path

val Path.name: String
    get() = fileName.toString()

fun Path.abs(): String = toAbsolutePath().normalize().toString()

fun getAndroidValidName(name: String): String {
    return name.toLowerCase().replace(Regex("[^a-z0-9_]"), "_")
}

fun px(dp: Int, density: Int): Int = (dp * density / 160.0).toInt()