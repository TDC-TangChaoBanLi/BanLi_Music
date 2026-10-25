/**
 * 文件作用：定义系统媒体控件使用的播放顺序自定义 SessionCommand。
 */
package com.banlimusic.app.player

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Cycles shuffle → list repeat → single repeat from the system media controls. */
object PlayOrderCommand {
    const val ACTION = "com.banlimusic.app.action.PLAY_ORDER"
    val sessionCommand = SessionCommand(ACTION, Bundle.EMPTY)
}
