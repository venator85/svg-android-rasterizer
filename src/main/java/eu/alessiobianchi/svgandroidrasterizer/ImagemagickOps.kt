package eu.alessiobianchi.svgandroidrasterizer

interface ImagemagickOp {
    fun toArgs(): List<String>
}

data class Padding(val padW: Int, val padH: Int) : ImagemagickOp {
    override fun toArgs(): List<String> {
        return listOf("-background", "none", "-gravity", "center", "-extent", "${padW}x$padH")
    }
}

data class Background(val rrggbb: String, val alpha: String = "") : ImagemagickOp {
    override fun toArgs(): List<String> {
        return listOf("-background", "#$rrggbb$alpha", "-flatten")
    }
}

object Round : ImagemagickOp {
    override fun toArgs(): List<String> {
        return listOf("-alpha", "set", "(", "+clone", "-distort", "DePolar", "0", "-virtual-pixel", "HorizontalTile",
                "-background", "None", "-distort", "Polar", "0", ")", "-compose", "Dst_In", "-composite", "-trim",
                "+repage")
    }

    override fun toString(): String {
        return javaClass.simpleName
    }
}
