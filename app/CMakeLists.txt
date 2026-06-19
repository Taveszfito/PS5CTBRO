cmake_minimum_required(VERSION 3.22.1)

project("ps5audio")

add_library(
        ps5audio
        SHARED
        src/main/cpp/native-lib.cpp
)

target_link_options(
        ps5audio
        PRIVATE
        "-Wl,-z,max-page-size=16384"
)

find_library(
        log-lib
        log
)

target_link_libraries(
        ps5audio
        ${log-lib}
)
