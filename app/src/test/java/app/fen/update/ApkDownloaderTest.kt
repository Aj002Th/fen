package app.fen.update

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `cancelling stops the transfer and removes the partial file`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val output = socket.getOutputStream()
                    output.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/octet-stream\r\n" +
                                "Content-Length: 100000000\r\n\r\n"
                            ).toByteArray(),
                    )
                    output.flush()
                    val block = ByteArray(8 * 1024)
                    repeat(100_000) {
                        output.write(block)
                        output.flush()
                    }
                }
            } catch (_: Exception) {
                // client disconnect / server close
            }
        }

        val dir = tmp.newFolder("updates")
        val downloader = ApkDownloader(dir, "Fen/test")
        val scope = CoroutineScope(Dispatchers.IO)
        var downloaded = 0L
        val job = scope.launch {
            downloader.download(
                url = "http://127.0.0.1:${server.localPort}/Fen-1.1.0.apk",
                fileName = "Fen-1.1.0.apk",
            ) { done, _ -> downloaded = done }
        }

        withTimeoutOrNull(5_000) {
            while (downloaded == 0L) delay(10)
        }

        job.cancel()
        val finished = withTimeoutOrNull(2_000) { job.join() }

        assertNotNull("cancel must finish the transfer, not just flip the UI", finished)
        assertTrue(job.isCancelled)
        assertTrue(
            "partial file must be gone after cancel",
            dir.listFiles().orEmpty().isEmpty(),
        )

        scope.cancel()
        server.close()
        serverThread.interrupt()
    }
}
