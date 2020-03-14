cmake_minimum_required(VERSION 3.4.1)


set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3 -flto")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -flto")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3 -flto")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -O3 -flto -fuse-ld=lld")


set(LIBTYPE STATIC)
set(ALSOFT_UTILS OFF)
set(ALSOFT_EXAMPLES OFF)
set(ALSOFT_CONFIG OFF)
set(ALSOFT_HRTF_DEFS OFF)
set(ALSOFT_AMBDEC OFF)
set(OPENAL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/openal-soft)
add_subdirectory(${OPENAL_DIR} ./openal-soft)
include_directories(${OPENAL_DIR}/include)


set(PNG_SHARED OFF)
set(PNG_TESTS OFF)
set(LIBPNG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/libpng)
add_subdirectory(${LIBPNG_DIR} ./libpng)
include_directories(${LIBPNG_DIR})

file(
	COPY ${LIBPNG_DIR}/scripts/pnglibconf.h.prebuilt
	DESTINATION ${LIBPNG_DIR}
)
file(
	RENAME ${LIBPNG_DIR}/pnglibconf.h.prebuilt
	${LIBPNG_DIR}/pnglibconf.h
)


set(GBCC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/gbcc)
include_directories(${GBCC_DIR}/src)

add_definitions(
        -DPRINTER_SOUND_PATH="/data/user/0/com.philj56.gbcc/files/print.wav"
        -DTILESET_PATH="/data/user/0/com.philj56.gbcc/files/tileset.png"
        -DSHADER_PATH="/data/user/0/com.philj56.gbcc/files/shaders/"
)

#set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3 -flto -fprofile-instr-use=${GBCC_DIR}/default.profdata")
#set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -flto -fprofile-instr-use=${GBCC_DIR}/default.profdata")
#set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3 -flto -fprofile-instr-use=${GBCC_DIR}/default.profdata")
#set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -flto -fprofile-instr-use=${GBCC_DIR}/default.profdata")
#set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -O3 -flto -fuse-ld=lld -fprofile-instr-use=${GBCC_DIR}/default.profdata")

add_library(gbcc SHARED
            gbcc.cpp
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
            ${GBCC_DIR}/src/menu.c
            ${GBCC_DIR}/src/ops.c
            ${GBCC_DIR}/src/palettes.c
            ${GBCC_DIR}/src/ppu.c
            ${GBCC_DIR}/src/printer.c
            ${GBCC_DIR}/src/save.c
            ${GBCC_DIR}/src/screenshot.c
            ${GBCC_DIR}/src/time_diff.c
            ${GBCC_DIR}/src/wav.c
            ${GBCC_DIR}/src/window.c
            ${GBCC_DIR}/src/vram_window.c
            ${GBCC_DIR}/src/wav.c)

target_link_libraries(gbcc
                      android
                      log
                      EGL
                      GLESv3
                      OpenAL
                      png_static)
