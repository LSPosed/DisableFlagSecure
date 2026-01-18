cmake_minimum_required(VERSION 3.10)

project("disableflagsecure")

find_library(log-lib log)

add_library(${CMAKE_PROJECT_NAME} SHARED
        main.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib})
