#!/bin/sh
mkdir buildSfolder
cd buildSfolder
cmake -G Ninja -DMOVESENSE_CORE_LIBRARY=../../../movesense-device-lib/MovesenseCoreLib/ -DCMAKE_TOOLCHAIN_FILE=../../../movesense-device-lib/MovesenseCoreLib/toolchain/gcc-nrf52.cmake ../bailu_app_standalone

ninja dfupkg
cp Movesense_dfu.zip ../movesense_dfu_s.zip
cd ..
rm -r buildSfolder
echo "Copying to ../src/main/res/raw"
cp movesense_dfu_s.zip ../src/main/res/raw/
