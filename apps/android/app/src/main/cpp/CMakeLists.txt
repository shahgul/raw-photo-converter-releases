cmake_minimum_required(VERSION 3.22.1)
project(rawconverter LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
# Source is unmodified, pinned, and checked; no camera SDKs or desktop tools.
FetchContent_Declare(libraw_source
    URL https://www.libraw.org/data/LibRaw-0.22.2.tar.gz
    URL_HASH SHA256=de86b035655accff8d4010f1a221fdf50d353cb7b1422ba26f14a0db92612cfa)
FetchContent_GetProperties(libraw_source)
if(NOT libraw_source_POPULATED)
    FetchContent_Populate(libraw_source)
endif()
file(GLOB_RECURSE LIBRAW_SOURCES CONFIGURE_DEPENDS "${libraw_source_SOURCE_DIR}/src/*.cpp")
# Upstream also ships *_ph.cpp placeholders; its Makefile deliberately excludes
# them because they duplicate the real processing/writer implementations.
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX "_ph\\.cpp$")
add_library(raw SHARED ${LIBRAW_SOURCES})
target_include_directories(raw PUBLIC "${libraw_source_SOURCE_DIR}")
target_compile_definitions(raw PUBLIC LIBRAW_NOTHREADS)
target_link_libraries(raw PRIVATE log)

add_library(rawconverter SHARED raw_decoder.cpp)
target_link_libraries(rawconverter PRIVATE raw jnigraphics log)
# 16 KiB page alignment for Android devices using large memory pages.
target_link_options(raw PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(rawconverter PRIVATE "-Wl,-z,max-page-size=16384")
