CC = arm-none-eabi-gcc
OBJCOPY = arm-none-eabi-objcopy
SIZE = arm-none-eabi-size

CFLAGS = -mcpu=cortex-m3 -mthumb -Os -Wall -Wextra -nostdlib -ffreestanding -fdata-sections -ffunction-sections
LDFLAGS = -Tstm32f103c8.ld -nostdlib -Wl,--gc-sections -Wl,-Map=firmware.map

SRCS = main.c startup.s
OBJS = $(SRCS:.c=.o)
OBJS := $(OBJS:.s=.o)

all: firmware.bin firmware.elf
	$(SIZE) firmware.elf

firmware.elf: $(OBJS) stm32f103c8.ld
	$(CC) $(CFLAGS) $(OBJS) $(LDFLAGS) -o $@

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

%.o: %.s
	$(CC) $(CFLAGS) -c $< -o $@

firmware.bin: firmware.elf
	$(OBJCOPY) -O binary $< $@

clean:
	rm -f $(OBJS) firmware.elf firmware.bin firmware.map

.PHONY: all clean
