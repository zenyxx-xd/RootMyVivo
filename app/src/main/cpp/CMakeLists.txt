cmake_minimum_required(VERSION 3.22)
project("rmv_native" C)

add_library(rmv_native SHARED
    rmv_native.c)

target_link_libraries(rmv_native PRIVATE log)
