/**
 * 文件作用：保证桌面端只运行一个实例，并把二次启动转发给已隐藏到托盘的窗口。
 */
package com.banlimusic.desktop

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * A file lock identifies the primary process; the primary listens on a loopback
 * socket so a second launch can ask the existing tray app to show its window.
 */
class DesktopSingleInstance private constructor(
    private val lockChannel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
    private val portFile: Path,
) : Closeable {

    @Volatile
    private var closed = false

    @Volatile
    var onShowRequest: (() -> Unit)? = null

    private val acceptThread = Thread(
        {
            while (!closed) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: IOException) {
                    continue
                }
                runCatching {
                    socket.use { client ->
                        client.soTimeout = READ_TIMEOUT_MS
                        val command = client.getInputStream()
                            .bufferedReader()
                            .readLine()
                            .orEmpty()
                            .trim()
                        if (command == COMMAND_SHOW) {
                            onShowRequest?.invoke()
                        }
                        client.getOutputStream().write("ok\n".toByteArray())
                        client.getOutputStream().flush()
                    }
                }
            }
        },
        "BanLiMusic-single-instance",
    ).apply { isDaemon = true }

    private fun start() {
        acceptThread.start()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        runCatching { Files.deleteIfExists(portFile) }
        runCatching { lock.release() }
        runCatching { lockChannel.close() }
        acceptThread.interrupt()
    }

    sealed interface AcquireResult {
        data class Primary(val instance: DesktopSingleInstance) : AcquireResult
        data class Secondary(val portFile: Path) : AcquireResult
    }

    companion object {
        private const val COMMAND_SHOW = "show"
        private const val CONNECT_TIMEOUT_MS = 600
        private const val READ_TIMEOUT_MS = 1_500
        private const val ACQUIRE_WAIT_MS = 5_000L

        fun acquire(): AcquireResult {
            DesktopPaths.ensureDirectories()
            val lockFile = DesktopPaths.configDir.resolve("instance.lock")
            val portFile = DesktopPaths.configDir.resolve("instance.port")
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (_: IOException) {
                null
            }
            if (lock == null) {
                runCatching { channel.close() }
                return AcquireResult.Secondary(portFile)
            }

            val server = try {
                ServerSocket(0, 4, InetAddress.getLoopbackAddress())
            } catch (error: IOException) {
                runCatching { lock.release() }
                runCatching { channel.close() }
                throw error
            }
            val instance = DesktopSingleInstance(channel, lock, server, portFile)
            instance.start()
            writePortFile(portFile, server.localPort)
            return AcquireResult.Primary(instance)
        }

        /** Asks the primary instance to restore its window; true when acknowledged. */
        fun requestShow(portFile: Path): Boolean {
            val deadline = System.currentTimeMillis() + ACQUIRE_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                val port = runCatching {
                    Files.readString(portFile).trim().toIntOrNull()
                }.getOrNull()
                if (port != null && port > 0) {
                    if (sendShow(port)) return true
                }
                Thread.sleep(100L)
            }
            return false
        }

        private fun sendShow(port: Int): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                    CONNECT_TIMEOUT_MS,
                )
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().write("$COMMAND_SHOW\n".toByteArray())
                socket.getOutputStream().flush()
                val reply = socket.getInputStream()
                    .bufferedReader()
                    .readLine()
                    .orEmpty()
                    .trim()
                reply == "ok"
            }
        }.getOrDefault(false)

        private fun writePortFile(portFile: Path, port: Int) {
            val temp = portFile.resolveSibling("${portFile.fileName}.tmp")
            Files.writeString(temp, port.toString())
            runCatching {
                Files.move(
                    temp,
                    portFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                Files.move(temp, portFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
