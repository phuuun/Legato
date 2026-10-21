package com.phuuun.legato.converter

private val illegal = Regex("""[/\\:*?"<>|]""")

/**
 * A file name Android accepts: no path characters, never empty, at most 120 characters before ".m4a". No leading dot
 * either: that makes a hidden file, which MediaStore never lists ("...Ready For It?").
 */
private fun safe(base: String): String = base.replace(illegal, "_").trimStart { it == '.' || it.isWhitespace() }.trimEnd().ifEmpty { "Untitled" }.take(120).trim()

fun musicFileName(artist: String, title: String) = safe(if (artist.isBlank()) title else "${artist.trim()} - ${title.trim()}") + ".m4a"

fun bookFileName(title: String) = safe(title) + ".m4a"

/** "03 The Will.m4a": the number keeps chapters in playlist order in any file manager. */
fun chapterFileName(position: Int, title: String) = safe("%02d %s".format(position, title.trim())) + ".m4a"

/** Never overwrite: a taken name becomes "Name (2).m4a", "Name (3).m4a", … */
fun uniqueName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val base = name.removeSuffix(".m4a")
    return generateSequence(2) { it + 1 }.map { "$base ($it).m4a" }.first { it !in taken }
}
