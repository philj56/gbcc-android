cmake_minimum_required(VERSION 3.4.1)

set(OPENAL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/openal-soft)
add_subdirectory(${OPENAL_DIR} ./openal-soft)
include_directories(${OPENAL_DIR}/include)

set(LIBPNG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/libpng)
add_subdirectory(${LIBPNG_DIR} ./libpng)
include_directories(${LIBPNG_DIR})

set(GBCC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/GBCC)


add_library(gbcc SHARED
            gbcc.cpp)

add_library(libgbcc SHARED
        ${GBCC_DIR}/src/apu.c
        ${GBCC_DIR}/src/args.c
        ${GBCC_DIR}/src/audio.c
        ${GBCC_DIR}/src/bit_utils.c
        ${GBCC_DIR}/src/colour.c
        ${GBCC_DIR}/src/config.c
        ${GBCC_DIR}/src/cpu.c
        ${GBCC_DIR}/src/debug.c
        ${GBCC_DIR}/src/fontmap.c
        ${GBCC_DIR}/src/gbcc.c
        ${GBCC_DIR}/src/hdma.c
        ${GBCC_DIR}/src/init.c
        ${GBCC_DIR}/src/input.c
        ${GBCC_DIR}/src/mbc.c
        ${GBCC_DIR}/src/memory.c
        ${GBCC_DIR}/src/ops.c
        ${GBCC_DIR}/src/palettes.c
        ${GBCC_DIR}/src/ppu.c
        ${GBCC_DIR}/src/printer.c
        ${GBCC_DIR}/src/save.c
        ${GBCC_DIR}/src/screenshot.c
        ${GBCC_DIR}/src/time.c
        ${GBCC_DIR}/src/wav.c
        ${GBCC_DIR}/src/window.c
        ${GBCC_DIR}/src/vram_window.c
        ${GBCC_DIR}/src/wav.c)

target_link_libraries(libgbcc
        OpenAL
        png)

target_link_libraries(gbcc
                      android
                      log
                      EGL
                      GLESv2
                      libgbcc)
