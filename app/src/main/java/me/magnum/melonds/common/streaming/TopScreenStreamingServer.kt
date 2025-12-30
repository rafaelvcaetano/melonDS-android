package me.magnum.melonds.common.streaming

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class TopScreenStreamingServer(
    private val screenshotBuffer: ByteBuffer,
    private val width: Int = 256,
    private val height: Int = 192,
) {
    @Volatile
    private var running = false
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    @Synchronized
    fun start(port: Int, fps: Int, quality: Int) {
        if (running) return
        running = true
        serverThread = Thread {
            runServer(port, fps, quality)
        }.apply { start() }
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            clientSocket?.close()
        } catch (_: IOException) {
        }
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        clientSocket = null
        serverSocket = null
        serverThread?.join(500)
        serverThread = null
    }

    private fun runServer(port: Int, fps: Int, quality: Int) {
        val targetFrameMs = max(1, 1000 / max(1, fps))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val jpegStream = ByteArrayOutputStream(width * height)

        try {
            ServerSocket(port).use { server ->
                server.reuseAddress = true
                serverSocket = server
                while (running) {
                    val socket = server.accept()
                    clientSocket = socket
                    socket.tcpNoDelay = true
                    streamClient(socket, bitmap, jpegStream, targetFrameMs, quality)
                    clientSocket = null
                }
            }
        } catch (_: IOException) {
        }
    }

    private fun streamClient(
        socket: Socket,
        bitmap: Bitmap,
        jpegStream: ByteArrayOutputStream,
        targetFrameMs: Int,
        quality: Int,
    ) {
        val pixels = IntArray(width * height)
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = input.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }

            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            if (path == "/stream") {
                writeHttpHeader(output, "multipart/x-mixed-replace; boundary=frame")
                while (running && !socket.isClosed) {
                    val frameStart = System.nanoTime()
                    val dup = screenshotBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    // Screenshot buffer stores top screen first, so we only copy the first 256x192 region.
                    dup.position(0)
                    dup.limit(width * height * 4)
                    dup.asIntBuffer().get(pixels, 0, pixels.size)
                    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                    jpegStream.reset()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)) {
                        continue
                    }
                    val jpegBytes = jpegStream.toByteArray()
                    output.write("--frame\r\n".toByteArray())
                    output.write("Content-Type: image/jpeg\r\n".toByteArray())
                    output.write("Content-Length: ${jpegBytes.size}\r\n\r\n".toByteArray())
                    output.write(jpegBytes)
                    output.write("\r\n".toByteArray())
                    output.flush()

                    val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000
                    val sleepMs = targetFrameMs - elapsedMs
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs.toLong())
                        } catch (_: InterruptedException) {
                        }
                    }
                }
            } else {
                writeHttpHeader(output, "text/html; charset=utf-8")
                val html = buildString {
                    append("<!doctype html>")
                    append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>")
                    append("<style>")
                    append("html,body{margin:0;height:100%;background:#000;}")
                    append("img{height:100vh;width:100vw;object-fit:contain;display:block;margin:0 auto;}")
                    append("button{position:fixed;top:12px;right:12px;z-index:10;padding:8px 12px;border:0;border-radius:6px;background:#222;color:#fff;font:14px sans-serif;opacity:.75;}")
                    append("button:hover{opacity:1;}")
                    append("</style></head>")
                    append("<body>")
                    append("<button id='fs'>Fullscreen</button>")
                    append("<img id='stream' src='/stream' alt='Top screen stream'>")
                    append("<script>")
                    append("const btn=document.getElementById('fs');")
                    append("btn.addEventListener('click',()=>{")
                    append("const el=document.documentElement;")
                    append("if(!document.fullscreenElement){el.requestFullscreen().catch(()=>{});}")
                    append("else{document.exitFullscreen().catch(()=>{});}")
                    append("});")
                    append("</script>")
                    append("</body></html>")
                }
                output.write(html.toByteArray())
                output.flush()
            }
        } catch (_: IOException) {
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun writeHttpHeader(output: BufferedOutputStream, contentType: String) {
        output.write("HTTP/1.1 200 OK\r\n".toByteArray())
        output.write("Cache-Control: no-store, no-cache, must-revalidate\r\n".toByteArray())
        output.write("Pragma: no-cache\r\n".toByteArray())
        output.write("Connection: close\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n\r\n".toByteArray())
        output.flush()
    }
}
