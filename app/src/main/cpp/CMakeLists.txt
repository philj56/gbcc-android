cmake_minimum_required(VERSION 3.4.1)
project(gbcc)

set(GBCC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/gbcc)

#set(FLAGS "-O3 -gfull -flto=full -fno-omit-frame-pointer -fprofile-instr-use=${GBCC_DIR}/default.profdata")
set(FLAGS "-O3 -pthread -gfull -flto=full -fno-omit-frame-pointer")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} ${FLAGS}")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} ${FLAGS}")
set(CMAKE_C_FLAGS_RELWITHDEBINFO  "${CMAKE_C_FLAGS_RELWITHDEBINFO} ${FLAGS}")
set(CMAKE_C_FLAGS_MINSIZEREL  "${CMAKE_C_FLAGS_MINSIZEREL} ${FLAGS}")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} ${FLAGS}")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} ${FLAGS}")
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "${CMAKE_CXX_FLAGS_RELWITHDEBINFO} ${FLAGS}")
set(CMAKE_CXX_FLAGS_MINSIZEREL "${CMAKE_CXX_FLAGS_MINSIZEREL} ${FLAGS}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${FLAGS}")

include_directories(${GBCC_DIR}/src)

add_definitions(
	-DPRINTER_SOUND_PATH="print.wav"
	-DTILESET_PATH="tileset.png"
	-DCAMERA_PATH="camera.png"
	-DSHADER_PATH="shaders/"
	)

add_library(gbcc SHARED
	gbcc.cpp
	${GBCC_DIR}/src/apu.c
	${GBCC_DIR}/src/args.c
	${GBCC_DIR}/src/audio.c
	${GBCC_DIR}/src/audio_platform/opensl.c
	${GBCC_DIR}/src/bit_utils.c
	${GBCC_DIR}/src/camera.c
	${GBCC_DIR}/src/cheats.c
	${GBCC_DIR}/src/colour.c
	${GBCC_DIR}/src/config.c
	${GBCC_DIR}/src/core.c
	${GBCC_DIR}/src/cpu.c
	${GBCC_DIR}/src/debug.c
	${GBCC_DIR}/src/gbcc.c
	${GBCC_DIR}/src/hdma.c
	${GBCC_DIR}/src/input.c
	${GBCC_DIR}/src/mbc.c
	${GBCC_DIR}/src/memory.c
	${GBCC_DIR}/src/menu.c
	${GBCC_DIR}/src/ops.c
	${GBCC_DIR}/src/palettes.c
	${GBCC_DIR}/src/ppu.c
	${GBCC_DIR}/src/printer.c
	${GBCC_DIR}/src/save.c
	${GBCC_DIR}/src/time_diff.c
	${GBCC_DIR}/src/wav.c
	${GBCC_DIR}/src/window.c
	${GBCC_DIR}/src/vram_window.c
	${GBCC_DIR}/src/wav.c)

target_link_libraries(gbcc
	log
	GLESv3
	OpenSLES)
