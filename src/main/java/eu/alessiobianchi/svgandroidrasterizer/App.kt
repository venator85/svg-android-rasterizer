package eu.alessiobianchi.svgandroidrasterizer

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence


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
    private val cacheFile = "generate_resource_cache.json"

    override fun run() {
        doResize()
    }

    fun doResize() {
        targetDensities.forEach {
            densities[it] ?: throw IllegalArgumentException("Unknown density $it")

            Files.createDirectories(outputPath.resolve("drawable-$it"))
        }

        val cache = readCache()

        val rasterizations = mutableListOf<Rasterization>()
        val padOps = mutableMapOf<Path, String>()

        inputSvgs().forEach { svg ->
            var ops = svg.name
                    .removeSuffix(".svg")
                    .split("~")
                    .toMutableList()

            val basename = getAndroidValidName(ops.removeAt(0))

            overrideOps?.apply {
                ops = split("~").toMutableList()
            }

            if (ops.isEmpty()) {
                throw IllegalArgumentException("No ops specified for $svg")
            }

            // if the svg hash has not changed AND there are no missing pngs, skip the generation
            val cachedHash = cache[svg.name]
            val calculatedHash = svg.sha256()
            cache[svg.name] = calculatedHash

            if (!force) {
                val hashMatches = if (cachedHash != null) {
                    calculatedHash == cachedHash
                } else {
                    false
                }

                val skip = if (hashMatches) {
                    val missingPngs = targetDensities.mapNotNull { dens ->
                        val pngPath = outFile(basename, dens)
                        if (Files.exists(pngPath)) null else pngPath
                    }
                    missingPngs.isEmpty()
                } else {
                    false
                }

                if (skip) {
                    return@forEach
                }
            }

            var outputs = emptyMap<Path, String>()
            var padSize: Pair<Int, Int>? = null

            ops.forEach { op ->
                if (op.startsWith("pad")) {
                    op.removePrefix("pad").split("x").apply {
                        if (size != 2) {
                            throw IllegalArgumentException("Invalid padding op $op")
                        }
                        padSize = Pair(get(0).toInt(), get(1).toInt())
                    }

                } else if (op.startsWith("tw")) {
                    val givenSize = op.removePrefix("tw").toInt()
                    outputs = processTwTh(basename, givenSize)
                            .mapValues { "${it.value}:" }

                } else if (op.startsWith("th")) {
                    val givenSize = op.removePrefix("th").toInt()
                    outputs = processTwTh(basename, givenSize)
                            .mapValues { ":${it.value}" }
                }
            }

            rasterizations.add(Rasterization(svg, outputs))

            padSize?.let {
                padOps.putAll(processPadding(basename, it))
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

        println("applying ${padOps.size} paddings...")
        runPadOps(padOps)

        val pngByRasterization = rasterizations
                .flatMap { it.outputs.keys }
                .map { it.abs() }
        val pngByPadOps = padOps.keys.map { it.abs() }
        val allPngs = (pngByRasterization + pngByPadOps).distinct()
        println("optimizing ${allPngs.size} generated images...")
        runPngOptimization(allPngs)

        println("updating cache...")
        writeCache(cache)
    }

    private fun processTwTh(basename: String, givenSize: Int): Map<Path, Int> {
        return targetDensities.map { dens ->
            val png = outFile(basename, dens)
            val pxSize = px(givenSize, densities[dens]!!)
            Pair(png, pxSize)
        }.toMap()
    }

    private fun processPadding(basename: String, padding: Pair<Int, Int>): Map<Path, String> {
        return targetDensities.map { dens ->
            val png = outFile(basename, dens)
            val padW = px(padding.first, densities[dens]!!)
            val padH = px(padding.second, densities[dens]!!)
            Pair(png, "${padW}x$padH")
        }.toMap()
    }

    private fun outFile(basename: String, dens: String) =
            outputPath.resolve("drawable-$dens").resolve("$basename.png")

    fun runSvgExport(svgexportOps: List<Map<String, Any>>) {
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

    fun runPadOps(padOps: Map<Path, String>) {
        padOps.forEach { path, padOp ->
            val retCode = ProcessBuilder(
                    "convert", path.abs(), "-background", "none", "-gravity", "center", "-extent", padOp, path.abs())
                    .inheritIO()
                    .start()
                    .waitFor()
            if (retCode != 0) {
                throw RuntimeException("convert failed with error code $retCode")
            }
        }
    }

    fun runPngOptimization(allPngs: List<String>) {
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

    fun readCache(): MutableMap<String, String> {
        return try {
            val text = cacheDir.resolve(cacheFile).readText()
            val json = JSONObject(text).toMap()
            json.entries.associate { it.key!! to it.value.toString() }.toMutableMap()
        } catch (e: IOException) {
            mutableMapOf()
        }
    }

    fun writeCache(cache: Map<String, String>) {
        try {
            val path = cacheDir.resolve(cacheFile)
            val text = JSONObject(cache).toString(2)
            Files.write(path, text.toByteArray())
        } catch (e: IOException) {
        }
    }

    fun inputSvgs(): Sequence<Path> = Files.walk(inputPath)
            .asSequence()
            .filter { it.name.endsWith(".svg", ignoreCase = true) }
}

private data class Rasterization(
        val svg: Path,
        val outputs: Map<Path, String>
) {
    override fun toString(): String {
        val s = StringBuilder().append("Rasterization: $svg:\n")
        outputs.forEach { k, v ->
            s.append("  $k -> $v\n")
        }
        return s.toString()
    }
}