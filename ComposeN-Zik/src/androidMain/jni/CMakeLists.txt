cmake_minimum_required(VERSION 3.22.1)
project(module_loader LANGUAGES C)

add_library(module_loader SHARED module_loader.c)
target_compile_features(module_loader PRIVATE c_std_11)
target_link_libraries(module_loader PRIVATE dl)

# Keep native debug information independent of checkout, build and NDK paths.
# https://f-droid.org/docs/Reproducible_Builds/#cmake
target_compile_options(module_loader PRIVATE
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
    "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=./build"
    "-ffile-prefix-map=${CMAKE_ANDROID_NDK}=./ndk"
    "-fdebug-compilation-dir=."
    "-fno-ident"
)
target_link_options(module_loader PRIVATE
    "LINKER:--build-id=none"
    "LINKER:--hash-style=gnu"
    "LINKER:-z,max-page-size=16384"
)
