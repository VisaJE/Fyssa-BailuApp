#!/bin/sh
mkdir buildSfolder
cd buildSfolder
cmake -G Ninja -DMOVESENSE_CORE_LIBRARY=../../MovesenseCoreLib/ -DCMAKE_TOOLCHAIN_FILE=../../MovesenseCoreLib/toolchain/gcc-nrf52.cmake ../bailu_app_standalone

ninja dfupkg
cp movesense_dfu.zip ../movesense_dfu_s.zip
cd ..
rm -r buildSfolder

cp movesense_dfu_s.zip ../src/main/res/raw/
