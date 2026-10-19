package com.phuuun.legato.ui.nowplaying

/** "3:07", or "1:02:03" past an hour. Unknown (C.TIME_UNSET is negative) and zero both read "0:00". */
fun formatTime(ms: Long): String {
    val s = if (ms > 0) ms / 1000 else 0
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
