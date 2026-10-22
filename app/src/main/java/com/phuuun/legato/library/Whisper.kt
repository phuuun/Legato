package com.phuuun.legato.library

/** whisper.cpp, Chapterfy's speech recognizer (app/src/main/cpp). Handles are native pointers; 0 is none. */
internal object Whisper {
    init { System.loadLibrary("legato_whisper") }

    external fun load(path: String): Long
    external fun newState(ctx: Long): Long
    /** What's said in [pcm] (16 kHz mono); null when Whisper failed. */
    external fun transcribe(ctx: Long, state: Long, pcm: FloatArray, prompt: String, audioCtx: Int, maxTokens: Int, threads: Int): ByteArray?
    external fun freeState(state: Long)
    external fun free(ctx: Long)
}

/** The model, loaded once and shared by every stretch listened to at once; each takes its own [Listener]. */
class WhisperModel(path: String) : AutoCloseable {
    private val ctx = Whisper.load(path).also { require(it != 0L) { "Couldn't load the speech model" } }

    fun listener() = Listener(ctx)

    override fun close() = Whisper.free(ctx)

    class Listener internal constructor(private val ctx: Long) : AutoCloseable {
        private val state = Whisper.newState(ctx).also { require(it != 0L) { "Couldn't start the speech model" } }

        /**
         * What's said in [pcm], 16 kHz mono, when it starts like a heading; else "". Most snippets aren't headings, so
         * the first 1.5 s are listened to on their own first (about a quarter of the work), and only a snippet that
         * starts with a heading word is listened to whole, for its number.
         */
        fun hear(pcm: FloatArray): String {
            val start = transcribe(pcm.copyOf(minOf(pcm.size, QUICK_SAMPLES)), QUICK_AUDIO_CTX, QUICK_TOKENS)
            return if (startsLikeHeading(start)) transcribe(pcm, AUDIO_CTX, TOKENS) else ""
        }

        private fun transcribe(pcm: FloatArray, audioCtx: Int, maxTokens: Int) =
            Whisper.transcribe(ctx, state, pcm, PROMPT, audioCtx, maxTokens, THREADS)?.toString(Charsets.UTF_8).orEmpty()

        override fun close() = Whisper.freeState(state)
    }

    private companion object {
        // Steers Whisper toward the way headings are written, so "Chapter 23" comes out, not "chapter twenty three".
        const val PROMPT = "Prologue. Chapter 1. Chapter 23. Part 2. Epilogue."
        // Whisper listens in 30 s windows (1500 steps of 20 ms); a snippet is 4 s, so only the first 300 steps (6 s) are
        // worked through, which is most of the speed-up. The quick listen is 1.5 s, in 75 steps, and needs a word or two.
        const val AUDIO_CTX = 300
        const val TOKENS = 24
        const val QUICK_SAMPLES = 24_000
        const val QUICK_AUDIO_CTX = 75
        const val QUICK_TOKENS = 4
        // Four stretches run at once (see listenBook), one thread each: on the phone, 4 × 1 beat 4 × 2 (23.7 s against
        // 30.7 s for the same snippets), as the extra threads only fought over the cores.
        const val THREADS = 1
    }
}

/** [pcm] (mono, at [rate]) as 16 kHz floats for Whisper: each output sample the average of the input it covers. */
fun to16k(pcm: ShortArray, n: Int, rate: Int): FloatArray {
    val out = FloatArray((n.toLong() * 16_000 / rate).toInt())
    for (j in out.indices) {
        val from = (j.toLong() * rate / 16_000).toInt()
        val to = (((j + 1).toLong() * rate / 16_000).toInt()).coerceIn(from + 1, n)
        var sum = 0f
        for (k in from until to) sum += pcm[k]
        out[j] = sum / (to - from) / 32768f
    }
    return out
}
