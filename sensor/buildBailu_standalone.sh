#!/bin/sh
mkdir buildSfolder
cd buildSfolder
cmake -G Ninja -DMOVESENSE_CORE_LIBRARY=../../../movesense-device-lib/MovesenseCoreLib/ -DCMAKE_TOOLCHAIN_FILE=../../../movesense-device-lib/MovesenseCoreLib/toolchain/gcc-nrf52.cmake ../bailu_app_standalone

ninja dfupkg
cp Movesense_dfu.zip ../movesense_dfu_s.zip
cp Movesense_dfu_w_bootloader.zip ../movesense_dfu_s_bootloader.zip
cd ..
rm -r buildSfolder
rm ../src/main/res/raw/movesense_dfu_s.zip
rm ../src/main/res/raw/movesense_dfu_s_bootloader.zip
echo "Copying to ../src/main/res/raw"
mv movesense_dfu_s.zip ../src/main/res/raw/
mv movesense_dfu_s_bootloader.zip ../src/main/res/raw/
