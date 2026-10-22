// Chapterfy's bridge to whisper.cpp: one model loaded once, one state per stretch listened to at once (see Whisper.kt).
#include <jni.h>
#include <stdint.h>
#include "whisper.h"

static void quiet(enum ggml_log_level level, const char * text, void * user_data) {}

JNIEXPORT jlong JNICALL
Java_com_phuuun_legato_library_Whisper_load(JNIEnv * env, jobject thiz, jstring path) {
    whisper_log_set(quiet, NULL);
    const char * p = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context * ctx = whisper_init_from_file_with_params_no_state(p, cparams);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong) (intptr_t) ctx;
}

JNIEXPORT jlong JNICALL
Java_com_phuuun_legato_library_Whisper_newState(JNIEnv * env, jobject thiz, jlong ctx) {
    return (jlong) (intptr_t) whisper_init_state((struct whisper_context *) (intptr_t) ctx);
}

JNIEXPORT jbyteArray JNICALL
Java_com_phuuun_legato_library_Whisper_transcribe(
        JNIEnv * env, jobject thiz, jlong ctx, jlong state, jfloatArray pcm, jstring prompt, jint audioCtx, jint maxTokens, jint threads) {
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = "en";
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.max_tokens = maxTokens;
    params.audio_ctx = audioCtx;
    params.temperature_inc = 0.0f; // no retries at higher temperatures: a heading is either heard or it isn't
    const char * p = (*env)->GetStringUTFChars(env, prompt, NULL);
    params.initial_prompt = p;

    jsize n = (*env)->GetArrayLength(env, pcm);
    jfloat * samples = (*env)->GetFloatArrayElements(env, pcm, NULL);
    int failed = whisper_full_with_state(
            (struct whisper_context *) (intptr_t) ctx, (struct whisper_state *) (intptr_t) state, params, samples, n);
    (*env)->ReleaseFloatArrayElements(env, pcm, samples, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, prompt, p);
    if (failed) return NULL;

    struct whisper_state * s = (struct whisper_state *) (intptr_t) state;
    char text[1024];
    size_t at = 0;
    for (int i = 0; i < whisper_full_n_segments_from_state(s); i++) {
        const char * t = whisper_full_get_segment_text_from_state(s, i);
        while (*t && at < sizeof(text) - 1) text[at++] = *t++;
    }
    // Bytes, not a jstring: NewStringUTF aborts on UTF-8 it doesn't accept (an emoji, a character cut at the end).
    jbyteArray out = (*env)->NewByteArray(env, (jsize) at);
    (*env)->SetByteArrayRegion(env, out, 0, (jsize) at, (const jbyte *) text);
    return out;
}

JNIEXPORT void JNICALL
Java_com_phuuun_legato_library_Whisper_freeState(JNIEnv * env, jobject thiz, jlong state) {
    whisper_free_state((struct whisper_state *) (intptr_t) state);
}

JNIEXPORT void JNICALL
Java_com_phuuun_legato_library_Whisper_free(JNIEnv * env, jobject thiz, jlong ctx) {
    whisper_free((struct whisper_context *) (intptr_t) ctx);
}
