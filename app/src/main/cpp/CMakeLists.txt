cmake_minimum_required(VERSION 3.22)
project(legato_whisper C CXX)

# whisper.cpp (git submodule, pinned to a release), CPU only, linked statically into one library. Its settings are
# option()s, which plain variables only override under this policy.
set(CMAKE_POLICY_DEFAULT_CMP0077 NEW)
set(BUILD_SHARED_LIBS OFF)
set(WHISPER_BUILD_TESTS OFF)
set(WHISPER_BUILD_EXAMPLES OFF)
set(WHISPER_BUILD_SERVER OFF)
set(GGML_OPENMP OFF)
set(GGML_NATIVE OFF)
# Every arm64 phone since about 2018 has these; they make the matrix maths several times faster.
set(GGML_CPU_ARM_ARCH armv8.2-a+dotprod+fp16)
add_subdirectory(whisper.cpp EXCLUDE_FROM_ALL)

add_library(legato_whisper SHARED whisper_jni.c)
target_link_libraries(legato_whisper whisper log)
target_link_options(legato_whisper PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384)
