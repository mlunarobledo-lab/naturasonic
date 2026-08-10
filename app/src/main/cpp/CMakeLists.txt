cmake_minimum_required(VERSION 3.22.1)
project("naturasonic" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

include(FetchContent)

# --- Oboe (audio pipeline) ---
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.9.0
)
FetchContent_MakeAvailable(oboe)

# --- whisper.cpp (transcription engine) ---
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2           OFF CACHE BOOL "" FORCE)
set(GGML_CUDA              OFF CACHE BOOL "" FORCE)
set(GGML_METAL             OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE        OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG v1.6.2
)
FetchContent_MakeAvailable(whisper)

# --- Unified audio + whisper library ---
add_library(naturasonic SHARED
    native-lib.cpp
    oboe_engine.cpp
    audio_processor.cpp
    volume_limiter.cpp
    whisper_bridge.cpp
)

target_include_directories(naturasonic PRIVATE
    ${oboe_SOURCE_DIR}/include
    ${whisper_SOURCE_DIR}
    ${whisper_SOURCE_DIR}/include
)

target_link_libraries(naturasonic
    oboe
    whisper
    android
    log
    OpenSLES
)
