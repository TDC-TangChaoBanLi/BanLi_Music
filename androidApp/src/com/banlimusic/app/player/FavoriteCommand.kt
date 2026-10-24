package com.banlimusic.app.player

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Custom session command exposed by the system media controls and the 4x1 widget. */
object FavoriteCommand {
    const val ACTION = "com.banlimusic.app.action.FAVORITE"
    val sessionCommand = SessionCommand(ACTION, Bundle.EMPTY)
}
