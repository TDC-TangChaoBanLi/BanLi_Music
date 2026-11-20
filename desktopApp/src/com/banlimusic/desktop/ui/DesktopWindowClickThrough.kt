/**
 * 文件作用：在 Windows 与 X11 下切换桌面悬浮窗的鼠标穿透。
 */
package com.banlimusic.desktop.ui

import androidx.compose.ui.awt.ComposeDialog
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Makes an undecorated Compose dialog click-through without hiding it. Windows
 * uses the layered-window transparent bit; X11 clears the Shape input region.
 * Unsupported systems keep the normal window behaviour.
 */
class DesktopWindowClickThrough(private val window: ComposeDialog) {

    private var backend: Backend? = null
    private var backendHandle: Long = 0L

    fun setClickThrough(enabled: Boolean) {
        val handle = runCatching { window.windowHandle }.getOrDefault(0L)
        if (handle == 0L) return
        if (handle != backendHandle) {
            backend?.close()
            backend = createBackend(handle, window.title)
            backendHandle = handle
        }
                val target = backend ?: return
        // Re-applied on every poll so AWT or the window manager cannot keep a
        // stale extended style after the window is shown or re-activated.
        runCatching { target.setEnabled(enabled) }
    }

    fun close() {
        runCatching { backend?.close() }
        backend = null
        backendHandle = 0L
    }

    private fun createBackend(handle: Long, title: String?): Backend? = runCatching {
        val os = System.getProperty("os.name").orEmpty()
        when {
            os.startsWith("Windows", ignoreCase = true) -> WindowsBackend(handle, title)
            os.contains("Linux", ignoreCase = true) -> LinuxBackend(handle)
            else -> null
        }
    }.getOrNull()

    private interface Backend {
        fun setEnabled(enabled: Boolean)
        fun close()
    }

    private class WindowsBackend(handle: Long, title: String?) : Backend {
        private val user32 = Native.load("user32", User32Ex::class.java)
        private val rawHwnd = Pointer(handle)
        // The Compose/Skia handle is the child canvas HWND. Hit testing and
        // transparency styles only take effect on the top-level dialog.
        private val hwnd: Pointer = run {
            val ancestor = user32.GetAncestor(rawHwnd, GA_ROOT)
            if (ancestor != null && ancestor != Pointer.NULL) {
                ancestor
            } else {
                val byTitle = title?.let { user32.FindWindowW(null, it) }
                if (byTitle != null && byTitle != Pointer.NULL) byTitle else rawHwnd
            }
        }
        override fun setEnabled(enabled: Boolean) {
            val current = user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
            val hasTransparent = (current and WS_EX_TRANSPARENT) != 0
            val hasLayered = (current and WS_EX_LAYERED) != 0
            if (enabled) {
                // Compose transparent windows are already WS_EX_LAYERED and
                // keep their per-pixel alpha. Do not call
                // SetLayeredWindowAttributes here: it replaces the DComp
                // surface with an opaque black rectangle. Wait for AWT to set
                // the layered bit, then add only the hit-test bit.
                if (hasLayered && !hasTransparent) {
                    user32.SetWindowLongW(
                        hwnd,
                        GWL_EXSTYLE,
                        current or WS_EX_TRANSPARENT,
                    )
                }
            } else {
                if (hasTransparent) {
                    user32.SetWindowLongW(
                        hwnd,
                        GWL_EXSTYLE,
                        current and WS_EX_TRANSPARENT.inv(),
                    )
                }
            }
        }

        override fun close() {
            runCatching { setEnabled(false) }
        }
    }

    private class LinuxBackend(handle: Long) : Backend {
        private val x11 = Native.load("X11", X11Ex::class.java)
        private val xext = Native.load("Xext", XextEx::class.java)
        private val display: Pointer? = x11.XOpenDisplay(null)
        private val xWindow = resolveTopLevel(display, NativeLong(handle))
        private var applied: Boolean? = null

        private fun resolveTopLevel(display: Pointer?, start: NativeLong): NativeLong {
            val dpy = display ?: return start
            var current = start
            while (true) {
                val root = NativeLongByReference()
                val parent = NativeLongByReference()
                val children = PointerByReference()
                val count = IntByReference()
                if (x11.XQueryTree(dpy, current, root, parent, children, count) == 0) {
                    return current
                }
                children.value?.let { x11.XFree(it) }
                val parentValue = parent.value ?: return current
                val rootValue = root.value
                if (parentValue.toLong() == 0L ||
                    (rootValue != null && parentValue.toLong() == rootValue.toLong())
                ) {
                    return current
                }
                current = parentValue
            }
        }

        override fun setEnabled(enabled: Boolean) {
            val dpy = display ?: return
            if (applied == enabled) return
            if (enabled) {
                // An empty input shape lets clicks fall through to the windows
                // below while the lyrics stay visible.
                xext.XShapeCombineRectangles(
                    dpy,
                    xWindow,
                    SHAPE_INPUT,
                    0,
                    0,
                    null,
                    0,
                    SHAPE_SET,
                    SHAPE_UNSORTED,
                )
            } else {
                // None removes the input shape, restoring the default region.
                xext.XShapeCombineMask(
                    dpy,
                    xWindow,
                    SHAPE_INPUT,
                    0,
                    0,
                    NativeLong(0),
                    SHAPE_SET,
                )
            }
            x11.XFlush(dpy)
            applied = enabled
        }

        override fun close() {
            runCatching { setEnabled(false) }
            display?.let { runCatching { x11.XCloseDisplay(it) } }
        }
    }

    private interface User32Ex : Library {
        fun GetAncestor(hWnd: Pointer, flags: Int): Pointer?
        fun FindWindowW(className: String?, windowName: String): Pointer?
        fun GetWindowLongW(hWnd: Pointer, nIndex: Int): Int
        fun SetWindowLongW(hWnd: Pointer, nIndex: Int, dwNewLong: Int): Int
    }

    private interface X11Ex : Library {
        fun XOpenDisplay(displayName: String?): Pointer?
        fun XCloseDisplay(display: Pointer): Int
        fun XFlush(display: Pointer)
        fun XQueryTree(
            display: Pointer,
            window: NativeLong,
            rootReturn: NativeLongByReference,
            parentReturn: NativeLongByReference,
            childrenReturn: PointerByReference,
            childCountReturn: IntByReference,
        ): Int

        fun XFree(data: Pointer): Int
    }

    private interface XextEx : Library {
        fun XShapeCombineRectangles(
            display: Pointer,
            window: NativeLong,
            destKind: Int,
            xOffset: Int,
            yOffset: Int,
            rectangles: Pointer?,
            count: Int,
            operation: Int,
            ordering: Int,
        ): Int

        fun XShapeCombineMask(
            display: Pointer,
            window: NativeLong,
            destKind: Int,
            xOffset: Int,
            yOffset: Int,
            source: NativeLong,
            operation: Int,
        ): Int
    }

    private companion object {
        const val GWL_EXSTYLE = -20
        const val WS_EX_TRANSPARENT = 0x00000020
        const val WS_EX_LAYERED = 0x00080000
        const val GA_ROOT = 2
        const val SHAPE_INPUT = 2
        const val SHAPE_SET = 0
        const val SHAPE_UNSORTED = 0
    }
}
