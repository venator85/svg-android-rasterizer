package eu.alessiobianchi.svgandroidrasterizer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.util.concurrent.Executors.newSingleThreadExecutor
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


@ExperimentalCoroutinesApi
class Gui {

    @OptIn(FlowPreview::class)
    fun run() {
        val coroutineScope = CoroutineScope(newSingleThreadExecutor().asCoroutineDispatcher())

        JFrame.setDefaultLookAndFeelDecorated(true)
        val frame = JFrame("svg-android-rasterizer").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            minimumSize = Dimension(1024, 600)
        }

        val svgTextArea = JTextArea().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            lineWrap = true
            wrapStyleWord = true
        }
        val svgScrollPane = JScrollPane(svgTextArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }


        val avdTextArea = JTextArea().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
        }
        val avdScrollPane = JScrollPane(avdTextArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }


        svgTextArea.dropTarget = object : DropTarget() {
            @Suppress("UNCHECKED_CAST")
            override fun drop(evt: DropTargetDropEvent) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY)
                    val droppedFiles = evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    val svgFile = droppedFiles.firstOrNull { it.name.endsWith(".svg", ignoreCase = true) }
                    if (svgFile != null) {
                        coroutineScope.launch {
                            try {
                                svgTextArea.text = svgFile.readText()
                                convert(svgFile, avdTextArea)
                            } catch (e: Exception) {
                                avdTextArea.text = e.stackTraceToString()
                            }
                        }
                    }

                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, svgScrollPane, avdScrollPane).apply {
            resizeWeight = 0.5
            isContinuousLayout = true
        }
        frame.contentPane.add(splitPane)
        frame.pack()
        frame.isVisible = true

        coroutineScope.launch {
            callbackFlow<String> {
                svgTextArea.document.addDocumentListener(object : DocumentListener {
                    override fun removeUpdate(e: DocumentEvent?) {
                        offer(svgTextArea.text.orEmpty())
                    }

                    override fun insertUpdate(e: DocumentEvent?) {
                        offer(svgTextArea.text.orEmpty())
                    }

                    override fun changedUpdate(e: DocumentEvent?) {
                        offer(svgTextArea.text.orEmpty())
                    }
                })
                awaitClose()
            }.debounce(200)
                .collect {
                    try {
                        val svgFile = File("/tmp/svg-android-rasterizer-temp.svg")
                        svgFile.writeText(it)
                        convert(svgFile, avdTextArea)
                    } catch (e: Exception) {
                        avdTextArea.text = e.stackTraceToString()
                    }
                }
        }
    }

    private fun convert(svgFile: File, avdTextArea: JTextArea) {
        val outFile = File("/tmp/svg-android-rasterizer-out.xml")
        val error = runAndroidVectorDrawableConversion(outFile.toPath(), svgFile.toPath())
        if (error.isNullOrEmpty()) {
            val out = outFile.readText()
            avdTextArea.text = out
        } else {
            avdTextArea.text = "!! $error"
        }
    }

}