# Phase 5.B — Official board references

URLs and document titles only. No copyrighted excerpts.

## Arduino UNO Rev3 (A000066)
- Arduino UNO R3 datasheet / connector pinouts — https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf
- Arduino UNO R3 full pinout PDF — https://docs.arduino.cc/resources/pinouts/A000066-full-pinout.pdf
- ATmega328P datasheet — Microchip DS40002061A — https://ww1.microchip.com/downloads/en/DeviceDoc/ATmega48A-PA-88A-PA-168A-PA-328-P-DS-DS40002061A.pdf

## Arduino Nano (A000005)
- Arduino Nano full pinout PDF — https://docs.arduino.cc/resources/pinouts/A000005-full-pinout.pdf

## Arduino Mega 2560 (A000067)
- Arduino Mega 2560 full pinout PDF — https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf

## Raspberry Pi Pico
- Pico datasheet — https://datasheets.raspberrypi.com/pico/pico-datasheet.pdf
- Pico R3 A4 pinout — https://datasheets.raspberrypi.com/pico/Pico-R3-A4-Pinout.pdf

## Raspberry Pi Pico W
- Pico W datasheet — https://datasheets.raspberrypi.com/picow/pico-w-datasheet.pdf

## Raspberry Pi Pico 2
- Pico 2 datasheet — https://datasheets.raspberrypi.com/pico/pico-2-datasheet.pdf

## NodeMCU DEVKIT V1.0
- NodeMCU DEVKIT V1.0 README / pin map — https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/README.md

## Wemos / LOLIN D1 Mini
- D1 Mini documentation — https://www.wemos.cc/en/latest/d1/d1_mini.html

## ESP32-DevKitC V4 (ESP32-WROOM-32E)
- ESP32-DevKitC V4 user guide — https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html
- ESP32 datasheet — https://www.espressif.com/sites/default/files/documentation/esp32_datasheet_en.pdf

## ESP32-C3-DevKitM-1
- ESP32-C3-DevKitM-1 user guide (J1/J3) — https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c3/esp32-c3-devkitm-1/user_guide.html

## Arduino Nano 33 IoT
- Arduino Nano 33 IoT hardware page — https://docs.arduino.cc/hardware/nano-33-iot

## Assumptions recorded in board metadata
- VIN/regulator paths are educational, not transistor-level models of the onboard LDO.
- ESP32 DAC on GPIO25/26 is hardware-only; analogWrite is PWM/LEDC, not true DAC.
- Pico W onboard LED is WL_GPIO0, not GP25.
- NodeMCU A0 full-scale voltage varies by revision; D1 Mini docs state 3.2 V max analog input.
- Mega Serial1/2/3 pins are labeled; the firmware subset drives Serial on D1/D0 only.
- No datasheet revision numbers were fabricated.
