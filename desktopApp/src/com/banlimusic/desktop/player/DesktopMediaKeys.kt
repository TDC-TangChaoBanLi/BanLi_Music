/**
 * 文件作用：捕获 Windows / Linux 的全局媒体按键并转发给桌面播放器。
 */
package com.banlimusic.desktop.player

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global media keys are not exposed by the JVM. Windows uses a low-level
 * keyboard hook, Linux uses a passive X11 grab. Both paths are best-effort:
 * unsupported desktops simply leave the keys to the operating system.
 */
class DesktopMediaKeys(private val player: DesktopAudioPlayer) : AutoCloseable {

    private var windowsHook: WindowsHook? = null
    private var linuxHook: LinuxHook? = null

    fun start() {
        runCatching {
            when {
                System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true) -> {
                    windowsHook = WindowsHook(::dispatch).also { it.start() }
                }

                System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true) -> {
                    linuxHook = LinuxHook(::dispatch).also { it.start() }
                }
            }
        }
    }

    private fun dispatch(action: MediaKeyAction) {
        if (player.state.value.track == null) return
        when (action) {
            MediaKeyAction.PLAY_PAUSE -> player.togglePause()
            MediaKeyAction.NEXT -> player.next()
            MediaKeyAction.PREVIOUS -> player.previous()
        }
    }

    override fun close() {
        runCatching { windowsHook?.close() }
        runCatching { linuxHook?.close() }
        windowsHook = null
        linuxHook = null
    }
}

enum class MediaKeyAction { PLAY_PAUSE, NEXT, PREVIOUS }

private class WindowsHook(
    private val dispatch: (MediaKeyAction) -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private var thread: Thread? = null
    private var hook: Pointer? = null
    private var callback: LowLevelKeyboardProc? = null
    private var threadId = 0

    fun start() {
        val worker = Thread({ runLoop() }, "BanLiMusic media keys").apply {
            isDaemon = true
        }
        thread = worker
        worker.start()
    }

    private fun runLoop() {
        val user32 = runCatching { Native.load("user32", User32Win::class.java) }.getOrNull()
            ?: return
        val proc = LowLevelKeyboardProc { nCode, wParam, lParam ->
            if (nCode >= 0 && !closed.get()) {
                val action = lParam?.let { readAction(it) }
                if (action != null && dispatchKey(action, wParam)) {
                    return@LowLevelKeyboardProc 1L
                }
            }
            user32.CallNextHookEx(null, nCode, wParam, lParam)
        }
        callback = proc
        threadId = user32.GetCurrentThreadId()
        hook = runCatching {
            user32.SetWindowsHookExW(
                WH_KEYBOARD_LL,
                proc,
                user32.GetModuleHandleW(null),
                0,
            )
        }.getOrNull()
        if (hook == null) return

        runCatching {
            val message = Memory(MSG_SIZE)
            while (!closed.get()) {
                val result = user32.GetMessageW(message, null, 0, 0)
                if (result <= 0) break
                user32.TranslateMessage(message)
                user32.DispatchMessageW(message)
            }
        }
        runCatching { user32.UnhookWindowsHookEx(hook) }
        hook = null
    }

    private fun dispatchKey(action: MediaKeyAction, wParam: Long): Boolean {
        val isDown = wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN
        val isUp = wParam == WM_KEYUP || wParam == WM_SYSKEYUP
        if (!isDown && !isUp) return false
        if (isDown) dispatch(action)
        // Consume the matching key-up as well so other players do not react.
        return true
    }

    private fun readAction(pointer: Pointer): MediaKeyAction? {
        val info = KbdLlHookStruct(pointer)
        info.read()
        if (info.flags and LLKHF_INJECTED != 0) return null
        return when (info.vkCode) {
            VK_MEDIA_PLAY_PAUSE, VK_MEDIA_STOP -> MediaKeyAction.PLAY_PAUSE
            VK_MEDIA_NEXT_TRACK -> MediaKeyAction.NEXT
            VK_MEDIA_PREV_TRACK -> MediaKeyAction.PREVIOUS
            else -> null
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val user32 = runCatching { Native.load("user32", User32Win::class.java) }.getOrNull()
        if (user32 != null) {
            hook?.let { runCatching { user32.UnhookWindowsHookEx(it) } }
            if (threadId != 0) {
                runCatching { user32.PostThreadMessageW(threadId, WM_QUIT, 0L, 0L) }
            }
        }
        hook = null
        thread?.interrupt()
        thread = null
        callback = null
    }

    private companion object {
        const val WH_KEYBOARD_LL = 13
        const val WM_KEYDOWN = 0x0100L
        const val WM_KEYUP = 0x0101L
        const val WM_SYSKEYDOWN = 0x0104L
        const val WM_SYSKEYUP = 0x0105L
        const val WM_QUIT = 0x0012
        const val LLKHF_INJECTED = 0x10
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_MEDIA_STOP = 0xB2
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
        const val MSG_SIZE = 64L
    }
}

private interface User32Win : Library {
    fun SetWindowsHookExW(idHook: Int, callback: LowLevelKeyboardProc, module: Pointer?, threadId: Int): Pointer?
    fun UnhookWindowsHookEx(hook: Pointer?): Boolean
    fun CallNextHookEx(hook: Pointer?, nCode: Int, wParam: Long, lParam: Pointer?): Long
    fun GetMessageW(message: Pointer, window: Pointer?, filterMin: Int, filterMax: Int): Int
    fun TranslateMessage(message: Pointer): Boolean
    fun DispatchMessageW(message: Pointer): Long
    fun GetModuleHandleW(name: String?): Pointer?
    fun GetCurrentThreadId(): Int
    fun PostThreadMessageW(threadId: Int, message: Int, wParam: Long, lParam: Long): Boolean
}

private fun interface LowLevelKeyboardProc : Callback {
    fun callback(nCode: Int, wParam: Long, lParam: Pointer?): Long
}

@Structure.FieldOrder("vkCode", "scanCode", "flags", "time", "dwExtraInfo")
private class KbdLlHookStruct(pointer: Pointer) : Structure(pointer) {
    @JvmField var vkCode: Int = 0
    @JvmField var scanCode: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var time: Int = 0
    @JvmField var dwExtraInfo: Pointer? = null
}

private class LinuxHook(
    private val dispatch: (MediaKeyAction) -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private var thread: Thread? = null
    private var display: Pointer? = null
    private var x11: X11? = null
    private var errorHandler: XErrorHandler? = null

    fun start() {
        val worker = Thread({ runLoop() }, "BanLiMusic media keys").apply {
            isDaemon = true
        }
        thread = worker
        worker.start()
    }

    private fun runLoop() {
        val lib = runCatching { Native.load("X11", X11::class.java) }.getOrNull() ?: return
        x11 = lib
        val handler = XErrorHandler { _, _ -> 0 }
        errorHandler = handler
        runCatching { lib.XSetErrorHandler(handler) }
        val opened = runCatching { lib.XOpenDisplay(null) }.getOrNull() ?: return
        display = opened
        val root = lib.XDefaultRootWindow(opened)
        MEDIA_KEY_SPECS.forEach { (keysym, action) ->
            val code = runCatching {
                lib.XKeysymToKeycode(opened, NativeLong(keysym)).toInt() and 0xFF
            }.getOrDefault(0)
            if (code <= 0) return@forEach
            grabbedActions[code] = action
            runCatching {
                lib.XGrabKey(opened, code, ANY_MODIFIER, root, false, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC)
            }
        }
        runCatching { lib.XSync(opened, false) }

        val event = Memory(XEVENT_SIZE)
        while (!closed.get()) {
            runCatching { lib.XNextEvent(opened, event) }.onFailure { break }
            if (closed.get()) break
            val keyEvent = XKeyEvent(event)
            keyEvent.read()
            if (keyEvent.type == KEY_PRESS) {
                actionForKeycode(keyEvent.keycode)?.let(dispatch)
            }
        }
        runCatching { lib.XCloseDisplay(opened) }
        display = null
    }

    private fun actionForKeycode(keycode: Int): MediaKeyAction? = when (keycode) {
        0 -> null
        else -> grabbedActions[keycode]
    }

    private val grabbedActions = mutableMapOf<Int, MediaKeyAction>()

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Do not call XCloseDisplay from another thread while XNextEvent is
        // blocked in the hook thread; the daemon thread exits with the JVM.
        display = null
        thread?.interrupt()
        thread = null
        x11 = null
        errorHandler = null
    }

    private companion object {
        const val XEVENT_SIZE = 192L
        const val KEY_PRESS = 2
        const val GRAB_MODE_ASYNC = 1
        const val ANY_MODIFIER = 1 shl 15
        val MEDIA_KEY_SPECS = listOf(
            0x1008FF14L to MediaKeyAction.PLAY_PAUSE, // XF86XK_AudioPlay
            0x1008FF15L to MediaKeyAction.PLAY_PAUSE, // XF86XK_AudioStop
            0x1008FF16L to MediaKeyAction.PREVIOUS, // XF86XK_AudioPrev
            0x1008FF17L to MediaKeyAction.NEXT, // XF86XK_AudioNext
            0x1008FF31L to MediaKeyAction.PLAY_PAUSE, // XF86XK_AudioPause
        )
    }
}

private interface X11 : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XCloseDisplay(display: Pointer?): Int
    fun XDefaultRootWindow(display: Pointer?): NativeLong
    fun XKeysymToKeycode(display: Pointer?, keysym: NativeLong): Byte
    fun XGrabKey(
        display: Pointer?,
        keycode: Int,
        modifiers: Int,
        grabWindow: NativeLong,
        ownerEvents: Boolean,
        pointerMode: Int,
        keyboardMode: Int,
    ): Int

    fun XNextEvent(display: Pointer?, event: Pointer?): Int
    fun XSync(display: Pointer?, discard: Boolean)
    fun XSetErrorHandler(handler: XErrorHandler): Pointer?
}

private fun interface XErrorHandler : Callback {
    fun callback(display: Pointer?, event: Pointer?): Int
}

@Structure.FieldOrder(
    "type",
    "serial",
    "sendEvent",
    "display",
    "window",
    "root",
    "subwindow",
    "time",
    "x",
    "y",
    "xRoot",
    "yRoot",
    "state",
    "keycode",
    "sameScreen",
)
private class XKeyEvent(pointer: Pointer) : Structure(pointer) {
    @JvmField var type: Int = 0
    @JvmField var serial: NativeLong = NativeLong(0)
    @JvmField var sendEvent: Int = 0
    @JvmField var display: Pointer? = null
    @JvmField var window: NativeLong = NativeLong(0)
    @JvmField var root: NativeLong = NativeLong(0)
    @JvmField var subwindow: NativeLong = NativeLong(0)
    @JvmField var time: NativeLong = NativeLong(0)
    @JvmField var x: Int = 0
    @JvmField var y: Int = 0
    @JvmField var xRoot: Int = 0
    @JvmField var yRoot: Int = 0
    @JvmField var state: Int = 0
    @JvmField var keycode: Int = 0
    @JvmField var sameScreen: Int = 0
}
