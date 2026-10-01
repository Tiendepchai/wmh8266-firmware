# WMH-8266 Firmware & Hardware Engineering 🔌

> Custom STM32 TuyaMCU firmware, pinned Tasmota release binary, and reverse engineering analysis for the WMH-8266 smart switch/relay.

## 📐 Hardware Architecture

The WMH-8266 board uses a dual-microcontroller architecture:

1. **ESP8266EX (Wi-Fi SoC)**:
   - Handles network stack, web server, MQTT, and telemetry via Tasmota.
   - Connected to the STM32 MCU via hardware UART (TX/RX) running the TuyaMCU serial protocol at 9600 baud.
2. **STM32F103 (Secondary MCU / TuyaMCU)**:
   - Controls physical relay switching, status LEDs, and buzzer / sound synthesis (`melody.h`).
   - Runs bare-metal Cortex-M3 C code (`stm32-firmware/`).

---

## 📂 Repository Contents

```
wmh8266-firmware/
├── tasmota.bin         # Pinned Tasmota binary tested & verified for WMH-8266
├── stm32-firmware/     # Custom bare-metal STM32F103 firmware (relays, buzzer, TuyaMCU)
│   ├── main.c          # MCU register-level driver & Tuya serial protocol handler
│   ├── melody.h        # Melody byte-sequences for buzzer chime
│   ├── parse_midi.py   # Python utility to convert MIDI to melody arrays
│   ├── startup.s       # Cortex-M3 startup vector table & reset handler
│   ├── stm32f103c8.ld  # GNU ld linker script
│   └── Makefile        # GCC ARM embedded build workflow
└── vendor-backup/      # Original factory ROM dump & Ghidra reverse engineering
    └── 20260929T140257Z-wmh8266-96u4cqud/
        ├── original.bin    # Factory firmware dump (esptool read_flash)
        ├── manifest.json   # ESP flash chip IDs and metadata
        └── analysis/       # Ghidra reverse engineering annotations and disassembly
```

---

## ⚡ Flashing Pinned Tasmota to ESP8266

### 1. Backup Factory Firmware (Optional but recommended)
```bash
esptool.py --port /dev/ttyUSB0 --baud 115200 read_flash 0x00000 0x100000 backup_original.bin
```

### 2. Flash Pinned Tasmota Binary
```bash
esptool.py --port /dev/ttyUSB0 --baud 460800 write_flash 0x00000 tasmota.bin
```

### 3. Tasmota Configuration
After booting Tasmota, configure Module as **TuyaMCU (54)**:
```
Module 54
Baudrate 9600
TuyaMCU 11,1   # Map Relay 1 to DPID 1
TuyaMCU 12,2   # Map Relay 2 to DPID 2
```

---

## 🛠 Building the STM32 Firmware

### Prerequisites
- `arm-none-eabi-gcc`
- `arm-none-eabi-objcopy`
- `make`

### Compile
```bash
cd stm32-firmware
make clean
make
```

Output binaries will be generated at `stm32-firmware/firmware.bin` and `firmware.elf`.

---

## 📄 License

MIT
