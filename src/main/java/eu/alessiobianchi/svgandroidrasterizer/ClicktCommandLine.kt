package eu.alessiobianchi.svgandroidrasterizer

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems

private fun description(): String {
    val version = App::class.java.getPackage().implementationVersion

    return """
SVG Android Rasterizer v$version

Author: Alessio Bianchi <me@alessiobianchi.eu>

Rasterizes SVG files to Android PNG resources using the operations specified in the SVG filename, or converts SVG files to Android vector drawables.

Each SVG filename must match the following pattern:

  <name>[~<op>]+.svg

or, in other words, a <name> followed by at least one '~<op>' directive.

The operation is used to determine the output PNG size in pixels. The following operations are supported:

- tw<dp> (target width). The target PNG will have the specified width in Android dp's. Aspect ratio will be preserved.

- th<dp> (target height). The target PNG will have the specified height in Android dp's. Aspect ratio will be preserved.

- pad<w_dp>x<h_dp> (padding). The PNG will be padded (with transparent background) so that its final dimensions will be exactly the ones specified (in dp).

- bg_<aarrggbb|rrggbb> (apply background color). The given background color will be used as background.

- round. The image will be cropped to round shape.

- mipmap. The resulting PNG will be placed in a mipmap (instead of drawable) subdirectory.

By default, if no ops are provided for a SVG file, it will be converted to an Android vector drawable.

Requirements:

- svgexport (https://github.com/shakiba/svgexport). On OS X, it can be installed via: npm install svgexport -g

- OptiPNG (http://optipng.sourceforge.net/). On OS X, it can be installed via: brew install optipng

- ImageMagick (http://www.imagemagick.org/). On OS X, it can be installed via: brew install imagemagick
"""
}

abstract class ClicktCommandLine : CliktCommand(help = description()) {

    private val defaultOutputPath = "app/src/main/generated-res/"
    private val defaultSvgexportOpsPath = "app/build/"
    private val defaultDensities = listOf("hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    val inputPaths by argument(help = "Input SVG files. Directories will be recurred into.")
        .path(mustExist = true, canBeFile = true, canBeDir = true, fileSystem = fileSystem)
        .multiple()

    val outputPath by option("-o", "--output", help = "Output directory for the generated PNGs, usually an Android res / directory (default: $defaultOutputPath)")
        .path(canBeFile = false, fileSystem = fileSystem)
        .default(fileSystem.getPath(defaultOutputPath))

    val svgexportOpsPath by option("-s", "--svgexport-ops-dir", help = "Directory for the svgexport input file (default: $defaultSvgexportOpsPath)")
        .path(canBeFile = false, fileSystem = fileSystem)
        .default(fileSystem.getPath(defaultSvgexportOpsPath))

    val overrideOps by option("--override-ops", help = "Override any operation declared in the svg filename (e.g. tw32~pad60x60)")

    private val _targetDensities by option("--densities", help = "The densities to generate raster images for, separated by comma (example and default: ${defaultDensities.joinToString(",")})")
    val targetDensities: List<String>
        get() {
            val densities = _targetDensities?.split(",") ?: defaultDensities
            return if (densities.isNotEmpty()) densities else defaultDensities
        }

    val skipFileWithoutOps by option("-z", "--skip", help = "Skip SVG files without ops in the name, instead of converting them to Android vector drawables").flag()

    val gui by option("-g", "--gui", help = "Shows a GUI to perform direct SVG -> Android VectorDrawable conversion").flag()

    protected val fileSystem: FileSystem
        get() = FileSystems.getDefault()

}
