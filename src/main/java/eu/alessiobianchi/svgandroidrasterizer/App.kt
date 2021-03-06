package eu.alessiobianchi.svgandroidrasterizer

import com.andreapivetta.kolor.red
import com.android.ide.common.vectordrawable.Svg2Vector
import org.json.JSONArray
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.*
import kotlin.streams.toList


val densities = mapOf(
        "ldpi" to 120,
        "mdpi" to 160,
        "hdpi" to 240,
        "xhdpi" to 320,
        "xxhdpi" to 480,
        "xxxhdpi" to 640
)

class App : ClicktCommandLine() {

    private val svgexportOpsFile = "svgexport_ops.json"

    override fun run() {
        if (gui) {
            Gui().run()
        } else {
            doResize()
        }
    }

    private fun doResize() {
        targetDensities.forEach {
            densities[it] ?: throw IllegalArgumentException("Unknown density $it")

            Files.createDirectories(outputPath.resolve("drawable-$it"))
        }

        val rasterizations = mutableListOf<Rasterization>()
        val androidVectorDrawableConversions = mutableListOf<AndroidVectorDrawableConversion>()
        val imagemagickOps = LinkedList<Pair<Path, ImagemagickOp>>()

        inputSvgs().forEach { svg ->
            var ops = svg.name
                    .removeSuffix(".svg")
                    .split("~")
                    .toMutableList()

            val basename = getAndroidValidName(ops.removeAt(0))

            overrideOps?.apply {
                ops = split("~").toMutableList()
            }

            var toAndroidVectorDrawable = false
            if (ops.isEmpty()) {
                if (skipFileWithoutOps) {
                    System.err.println("skipping file without ops: $svg")
                    return@forEach
                } else {
                    toAndroidVectorDrawable = true
                }
            }

            if (!toAndroidVectorDrawable) {
                var outputs = emptyMap<Path, String>()
                val mipmap = ops.contains("mipmap")
                var padSize: Pair<Int, Int>? = null
                var bgColor: Background? = null
                var round = false

                ops.forEach { op ->
                    if (op.startsWith("pad")) {
                        op.removePrefix("pad").split("x").apply {
                            require(size == 2) { "Invalid padding op $op" }
                            padSize = Pair(get(0).toInt(), get(1).toInt())
                        }

                    } else if (op.startsWith("tw")) {
                        val givenSize = op.removePrefix("tw").toInt()
                        outputs = processTwTh(basename, givenSize, mipmap)
                            .mapValues { "${it.value}:" }

                    } else if (op.startsWith("th")) {
                        val givenSize = op.removePrefix("th").toInt()
                        outputs = processTwTh(basename, givenSize, mipmap)
                            .mapValues { ":${it.value}" }

                    } else if (op.startsWith("bg_")) {
                        val colorStr = op.removePrefix("bg_")
                        val rrggbb: String
                        val alpha: String
                        when (colorStr.length) {
                            8 -> {
                                alpha = colorStr.substring(0, 2)
                                rrggbb = colorStr.substring(2)
                            }
                            6 -> {
                                alpha = ""
                                rrggbb = colorStr
                            }
                            else -> throw IllegalArgumentException("Invalid background color $op")
                        }
                        bgColor = Background(rrggbb, alpha)

                    } else if (op == ("round")) {
                        round = true
                    }
                }

                rasterizations.add(Rasterization(svg, outputs))

                padSize?.let { (w, h) ->
                    imagemagickOps.addAll(prepareOp(basename, mipmap) { dens ->
                        val padW = px(w, dens)
                        val padH = px(h, dens)
                        Padding(padW, padH)
                    })
                }

                bgColor?.let { bg ->
                    imagemagickOps.addAll(prepareOp(basename, mipmap) { bg })
                }

                if (round) {
                    imagemagickOps.addAll(prepareOp(basename, mipmap) { Round })
                }

            } else {
                val outFile = outFileAndroidVectorDrawable(basename)
                androidVectorDrawableConversions += AndroidVectorDrawableConversion(svg, outFile)
            }
        }

        println("performing ${rasterizations.size} rasterizations...")
        val svgexportOps = rasterizations.map { r ->
            val input = listOf(r.svg.abs())
            val output = r.outputs.map { (f, op) ->
                listOf(f.abs(), op)
            }.toList()
            mapOf<String, Any>("input" to input, "output" to output)
        }
        runSvgExport(svgexportOps)

        println("applying ${imagemagickOps.size} Imagemagick operations...")
        runImagemagickOps(imagemagickOps)

        val pngByRasterization = rasterizations
                .flatMap { it.outputs.keys }
                .map { it.abs() }
        val pngByImagemagickOps = imagemagickOps.toMap().keys.map { it.abs() }
        val allPngs = (pngByRasterization + pngByImagemagickOps).distinct()
        println("optimizing ${allPngs.size} generated images...")
        runPngOptimization(allPngs)

        println("performing ${androidVectorDrawableConversions.size} Android vector drawable conversions...")
        runAndroidVectorDrawableConversions(androidVectorDrawableConversions)
    }

    private fun processTwTh(basename: String, givenSize: Int, mipmap: Boolean): Map<Path, Int> {
        return targetDensities.map { dens ->
            val png = outFile(basename, dens, mipmap)
            val pxSize = px(givenSize, densities.getValue(dens))
            Pair(png, pxSize)
        }.toMap()
    }

    private fun prepareOp(basename: String, mipmap: Boolean, factory: (density: Int) -> ImagemagickOp): List<Pair<Path, ImagemagickOp>> {
        return targetDensities.map { dens ->
            val png = outFile(basename, dens, mipmap)
            val dpi = densities.getValue(dens)
            Pair(png, factory(dpi))
        }
    }

    private fun outFile(basename: String, dens: String, mipmap: Boolean): Path {
        val dirName = if (mipmap) "mipmap" else "drawable"
        return outputPath.resolve("$dirName-$dens").resolve("$basename.png")
    }

    private fun outFileAndroidVectorDrawable(basename: String): Path {
        return outputPath.resolve("drawable").resolve("$basename.xml")
    }

    private fun runSvgExport(svgexportOps: List<Map<String, Any>>) {
        if (svgexportOps.isEmpty()) {
            return
        }

        Files.createDirectories(svgexportOpsPath)
        val f = svgexportOpsPath.resolve(svgexportOpsFile)
        val json = JSONArray(svgexportOps).toString(2)
        Files.write(f, json.toByteArray())

        val retCode = ProcessBuilder("svgexport", f.abs())
                .inheritIO()
                .start()
                .waitFor()

        if (retCode != 0) {
            throw RuntimeException("svgexport failed with error code $retCode")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun runImagemagickOps(imagemagickOps: List<Pair<Path, ImagemagickOp>>) {
        imagemagickOps.forEach { (path, op) ->
            val cmd = mutableListOf("convert", path.abs())
            cmd.addAll(op.toArgs())
            cmd.add(path.abs())
            val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
            val stdouterr = process.inputStream.readBytes()
            val retCode = process.waitFor()
            if (retCode != 0) {
                println(":: $cmd\n")
                System.out.write(stdouterr)
                println()
                throw RuntimeException("Imagemagick op failed with error code $retCode")
            }
        }
    }

    private fun runPngOptimization(allPngs: List<String>) {
        allPngs.forEach {
            val retCode = ProcessBuilder("optipng", "-quiet", it)
                    .inheritIO()
                    .start()
                    .waitFor()
            if (retCode != 0) {
                throw RuntimeException("optipng failed with error code $retCode")
            }
        }
    }

    private fun runAndroidVectorDrawableConversions(list: List<AndroidVectorDrawableConversion>) {
        list.forEach {(source, dest) ->
            val error = runAndroidVectorDrawableConversion(dest, source)
            if (error != null && error.isNotEmpty()) {
                Files.deleteIfExists(dest)
                System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n!! Error during Android vector drawable conversion: $error\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!".red())
            }
        }
    }

    fun inputSvgs(): Iterable<Path> {
        return inputPaths.flatMap {
            if (Files.isDirectory(it)) {
                Files.walk(it).toList()
            } else {
                listOf(it)
            }
        }.filter {
            it.name.endsWith(".svg", ignoreCase = true)
        }.distinct()
    }
}

fun runAndroidVectorDrawableConversion(dest: Path, source: Path): String? {
    try {
        Files.createDirectories(dest.parent)
    } catch (ignore: FileAlreadyExistsException) {
    }
    return Files.newOutputStream(dest, CREATE, TRUNCATE_EXISTING).use {
        Svg2Vector.parseSvgToXml(source.toFile(), it)
    }
}

private data class Rasterization(
        val svg: Path,
        val outputs: Map<Path, String>
) {
    override fun toString(): String {
        val s = StringBuilder().append("Rasterization: $svg:\n")
        outputs.forEach { (k, v) ->
            s.append("  $k -> $v\n")
        }
        return s.toString()
    }
}

private data class AndroidVectorDrawableConversion(
    val svg: Path,
    val output: Path
) {
    override fun toString(): String = "AndroidVectorDrawableConversion: $svg -> $output"
}
