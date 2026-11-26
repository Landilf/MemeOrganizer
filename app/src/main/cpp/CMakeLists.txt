cmake_minimum_required(VERSION 3.22.1)
project(meme_native LANGUAGES CXX)

# DJL's Android tokenizer depends on the shared Android C++ runtime.
add_library(meme_native_runtime SHARED runtime.cpp)
