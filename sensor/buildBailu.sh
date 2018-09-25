#!/bin/sh
mkdir buildfolder
cd buildfolder
cmake -G Ninja -DMOVESENSE_CORE_LIBRARY=../MovesenseCoreLib/ -DCMAKE_TOOLCHAIN_FILE=../MovesenseCoreLib/toolchain/gcc-nrf52.cmake ../bailu_app

ninja dfupkg
cp movesense_dfu.zip ..
cd ..
rm -r buildfolder

