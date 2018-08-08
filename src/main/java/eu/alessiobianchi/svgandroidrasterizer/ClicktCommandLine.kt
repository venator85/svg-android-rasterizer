package eu.alessiobianchi.svgandroidrasterizer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.io.FileNotFoundException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files

private fun description(): String {
    val version = App::class.java.getPackage().implementationVersion

    return """
SVG Android Rasterizer v$version

Author: Alessio Bianchi <me@alessiobianchi.eu>

Rasterizes SVG files to Android PNG resources using the operations specified in the SVG filename.

Each SVG filename must match the following pattern:

  <name>[~<op>]+.svg

or, in other words, a <name> followed by at least one '~<op>' directive.

The operation is used to determine the output PNG size in pixels. The following operations are supported:

- tw<dp> (target width). The target PNG will have the specified width in Android dp's. Aspect ratio will be preserved.

- th<dp> (target height). The target PNG will have the specified height in Android dp's. Aspect ratio will be preserved.

- pad<w_dp>x<h_dp> (padding). The PNG will be padded (with transparent background) so that its final dimensions will be exactly the ones specified (in dp).

- mipmap. The resulting PNG will be placed in a mipmap (instead of drawable) subdirectory.

Requirements:

- svgexport (https://github.com/shakiba/svgexport). On OS X, it can be installed via: npm install svgexport -g

- OptiPNG (http://optipng.sourceforge.net/). On OS X, it can be installed via: brew install optipng

- ImageMagick (http://www.imagemagick.org/). On OS X, it can be installed via: brew install imagemagick
"""
}

abstract class ClicktCommandLine : CliktCommand(help = description()) {

    private val defaultInputPath = "app/src/main/svg-png/"
    private val defaultOutputPath = "app/src/main/generated-res/"
    private val defaultSvgexportOpsPath = "app/build/"
    private val defaultCacheDirPath = "app/build/"
    private val defaultDensities = arrayListOf("hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    val inputPath by option("-i", "--input", help = "Input directory containing the SVG files (default: $defaultInputPath)")
            .path(fileSystem = fileSystem, fileOkay = false, exists = true)
            .defaultLazy {
                val path = fileSystem.getPath(defaultInputPath)
                if (!Files.exists(path)) {
                    throw FileNotFoundException("Input directory $path doesn't exist")
                }
                path
            }

    val outputPath by option("-o", "--output", help = "Output directory for the generated PNGs, usually an Android res / directory (default: $defaultOutputPath)")
            .path(fileSystem = fileSystem, fileOkay = false)
            .default(fileSystem.getPath(defaultOutputPath))

    val svgexportOpsPath by option("-s", "--svgexport-ops-dir", help = "Directory for the svgexport input file (default: $defaultSvgexportOpsPath)")
            .path(fileSystem = fileSystem, fileOkay = false)
            .default(fileSystem.getPath(defaultSvgexportOpsPath))

    val cacheDir by option("-c", "--cache-dir", help = "Directory for the cache file (default: $defaultCacheDirPath)")
            .path(fileSystem = fileSystem, fileOkay = false)
            .default(fileSystem.getPath(defaultCacheDirPath))

    val overrideOps by option("--override-ops", help = "Override any operation declared in the svg filename (e.g. tw32~pad60x60)")

    val force by option("-f", "--force", help = "Force regeneration of all images skipping the cache.")
            .flag()

    private val _targetDensities by argument("--densities", help = "The densities to generate raster images for (default: ${defaultDensities.joinToString(", ")})")
            .multiple()
    val targetDensities: List<String>
        get() = if (_targetDensities.isEmpty()) defaultDensities else _targetDensities


    protected val fileSystem: FileSystem
        get() = FileSystems.getDefault()

}
