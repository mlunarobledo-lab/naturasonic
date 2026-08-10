cmake_minimum_required(VERSION 3.22.1)
project("naturasonic" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe — fetched via CPM or as a subdirectory
include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.9.0
)
FetchContent_MakeAvailable(oboe)

add_library(naturasonic SHARED
    native-lib.cpp
    oboe_engine.cpp
    audio_processor.cpp
    volume_limiter.cpp
)

target_include_directories(naturasonic PRIVATE
    ${oboe_SOURCE_DIR}/include
)

target_link_libraries(naturasonic
    oboe
    android
    log
    OpenSLES
)
