#!/bin/sh
mkdir buildfolder
cd buildfolder
cmake -G Ninja -DMOVESENSE_CORE_LIBRARY=../../../movesense-device-lib/MovesenseCoreLib/ -DCMAKE_TOOLCHAIN_FILE=../../../movesense-device-lib/MovesenseCoreLib/toolchain/gcc-nrf52.cmake ../bailu_app

ninja dfupkg
cp Movesense_dfu.zip ../movesense_dfu.zip
cp Movesense_dfu_w_bootloader.zip ../movesense_dfu_bootloader.zip
cd ..
rm -r buildfolder
echo "Copying zip to ../src/main/res/raw"
cp movesense_dfu.zip ../src/main/res/raw/
cp movesense_dfu_bootloader.zip ../src/main/res/raw/
