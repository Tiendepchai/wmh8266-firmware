#include <stdint.h>
#include <stdbool.h>
#include "melody.h"

/* --- Hardware Register Definitions --- */
#define DEMCR             (*(volatile uint32_t *)0xE000EDFC)
#define DWT_CTRL          (*(volatile uint32_t *)0xE0001000)
#define DWT_CYCCNT        (*(volatile uint32_t *)0xE0001004)

#define STK_CTRL          (*(volatile uint32_t *)0xE000E010)
#define STK_LOAD          (*(volatile uint32_t *)0xE000E014)
#define STK_VAL           (*(volatile uint32_t *)0xE000E018)

#define FLASH_ACR         (*(volatile uint32_t *)0x40022000)

#define RCC_CR            (*(volatile uint32_t *)0x40021000)
#define RCC_CFGR          (*(volatile uint32_t *)0x40021004)
#define RCC_APB2ENR       (*(volatile uint32_t *)0x40021018)
#define RCC_APB1ENR       (*(volatile uint32_t *)0x4002101C)

#define AFIO_MAPR         (*(volatile uint32_t *)0x40010004)

#define GPIOA_CRL         (*(volatile uint32_t *)0x40010800)
#define GPIOA_CRH         (*(volatile uint32_t *)0x40010804)
#define GPIOA_IDR         (*(volatile uint32_t *)0x40010808)
#define GPIOA_BSRR        (*(volatile uint32_t *)0x40010810)
#define GPIOA_BRR         (*(volatile uint32_t *)0x40010814)

#define GPIOB_CRH         (*(volatile uint32_t *)0x40010C04)
#define GPIOB_BSRR        (*(volatile uint32_t *)0x40010C10)
#define GPIOB_BRR         (*(volatile uint32_t *)0x40010C14)

#define USART3_SR         (*(volatile uint32_t *)0x40004800)
#define USART3_DR         (*(volatile uint32_t *)0x40004804)
#define USART3_BRR        (*(volatile uint32_t *)0x40004808)
#define USART3_CR1        (*(volatile uint32_t *)0x4000480C)

/* --- Tuya Protocol Constants --- */
#define TUYA_HEADER_1       0x55
#define TUYA_HEADER_2       0xAA
#define TUYA_VER            0x00

#define TUYA_CMD_HEARTBEAT  0x00
#define TUYA_CMD_PROD_INFO  0x01
#define TUYA_CMD_WORK_MODE  0x02
#define TUYA_CMD_WIFI_STATE 0x03
#define TUYA_CMD_SET_DP     0x06
#define TUYA_CMD_REPORT_DP  0x07
#define TUYA_CMD_QUERY_DP   0x08

#define TUYA_DP_RELAY       1
#define TUYA_DP_CHIME       2
#define TUYA_TYPE_BOOL      1

/* --- System State --- */
volatile uint32_t g_ticks = 0;
static uint32_t g_last_heartbeat_tick = 0;
static uint8_t  g_relay_state = 0;

void SysTick_Handler(void) {
    g_ticks++;
}

/* --- Hardware Functions --- */
static void delay_ms(uint32_t ms) {
    uint32_t start = g_ticks;
    while ((g_ticks - start) < ms) {
        __asm__ volatile ("nop");
    }
}

// ponytail: busy-wait DWT cycle loop; upgrade to TIM PWM if non-blocking buzzer needed
static void buzzer_tone_duty(uint32_t freq_hz, uint32_t ms, uint8_t duty_start, uint8_t duty_end) {
    if (freq_hz == 0 || ms == 0) {
        delay_ms(ms);
        return;
    }
    uint32_t period = 64000000 / freq_hz;
    uint32_t total_cycles = (freq_hz * ms) / 1000;

    for (uint32_t i = 0; i < total_cycles; i++) {
        uint32_t duty = duty_start - ((duty_start - duty_end) * i) / total_cycles;
        if (duty < 4) duty = 4;
        uint32_t high_time = (period * duty) / 100;
        uint32_t low_time = period - high_time;

        GPIOA_BSRR = (1 << 15); // HIGH
        uint32_t start = DWT_CYCCNT;
        while ((DWT_CYCCNT - start) < high_time) {}

        GPIOA_BRR = (1 << 15);  // LOW
        start = DWT_CYCCNT;
        while ((DWT_CYCCNT - start) < low_time) {}
    }
}

static void buzzer_westminster_8(void) {
    // 8-note Westminster Chimes (Big Ben Half-Hour Chime)
    // Phrase 1: C6 -> E6 -> D6 -> G5
    buzzer_tone_duty(NOTE_C6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_E6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_D6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_G5, 320, 25, 8);
    delay_ms(90);

    // Phrase 2: C6 -> D6 -> E6 -> C6
    buzzer_tone_duty(NOTE_C6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_D6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_E6, 160, 24, 12);
    delay_ms(30);
    buzzer_tone_duty(NOTE_C6, 500, 26, 6);
}

static void buzzer_smart_cascade(void) {
    // 5-note Luxury Smart Flourish (Apple / Google Nest style)
    buzzer_tone_duty(NOTE_G5,  75, 22, 14);
    delay_ms(15);
    buzzer_tone_duty(NOTE_C6,  80, 24, 14);
    delay_ms(15);
    buzzer_tone_duty(NOTE_E6,  85, 24, 14);
    delay_ms(15);
    buzzer_tone_duty(NOTE_G6,  95, 25, 12);
    delay_ms(20);
    buzzer_tone_duty(NOTE_C7, 450, 26,  6);
}

static void buzzer_play_melody(uint8_t mel) {
    if (mel == 0) {
        buzzer_westminster_8();
    } else if (mel == 1) {
        // Relay ON: crisp 2-note ascending chime
        buzzer_tone_duty(NOTE_E6, 60, 24, 14);
        delay_ms(15);
        buzzer_tone_duty(NOTE_C7, 130, 26, 8);
    } else if (mel == 2) {
        // Relay OFF: soft 2-note descending chime
        buzzer_tone_duty(NOTE_C7, 60, 24, 14);
        delay_ms(15);
        buzzer_tone_duty(NOTE_G5, 140, 22, 8);
    } else if (mel == 3) {
        buzzer_smart_cascade();
    }
}

static void led_yellow_set(bool on) {
    if (on) {
        GPIOA_BRR = (1 << 8);  // Active-LOW: LOW = ON
    } else {
        GPIOA_BSRR = (1 << 8); // HIGH = OFF
    }
}

static void led_red_set(bool on) {
    if (on) {
        GPIOA_BRR = (1 << 9);  // Active-LOW: LOW = ON
    } else {
        GPIOA_BSRR = (1 << 9); // HIGH = OFF
    }
}

static void relay_set(uint8_t state) {
    g_relay_state = state ? 1 : 0;
    if (g_relay_state) {
        GPIOA_BSRR = (1 << 1); // PA1 HIGH
        led_red_set(true);
    } else {
        GPIOA_BRR = (1 << 1);  // PA1 LOW
        led_red_set(false);
    }
}

static void usart3_send_byte(uint8_t b) {
    while (!(USART3_SR & (1 << 7))) {} // Wait TXE
    USART3_DR = b;
}

static void tuya_send_frame(uint8_t cmd, const uint8_t *data, uint16_t len) {
    uint32_t sum = 0;

    usart3_send_byte(TUYA_HEADER_1); sum += TUYA_HEADER_1;
    usart3_send_byte(TUYA_HEADER_2); sum += TUYA_HEADER_2;
    usart3_send_byte(TUYA_VER);      sum += TUYA_VER;
    usart3_send_byte(cmd);           sum += cmd;

    uint8_t len_h = (len >> 8) & 0xFF;
    uint8_t len_l = len & 0xFF;
    usart3_send_byte(len_h); sum += len_h;
    usart3_send_byte(len_l); sum += len_l;

    for (uint16_t i = 0; i < len; i++) {
        usart3_send_byte(data[i]);
        sum += data[i];
    }

    usart3_send_byte((uint8_t)(sum & 0xFF));
}

static void tuya_report_dp(uint8_t dpid, uint8_t state) {
    uint8_t payload[5] = {
        dpid,           // dpID
        TUYA_TYPE_BOOL, // type = bool (0x01)
        0x00, 0x01,     // len = 1
        state ? 0x01 : 0x00
    };
    tuya_send_frame(TUYA_CMD_REPORT_DP, payload, 5);
}

/* --- Tuya Frame Parser --- */
typedef enum {
    P_WAIT_55 = 0,
    P_WAIT_AA,
    P_VER,
    P_CMD,
    P_LEN_H,
    P_LEN_L,
    P_DATA,
    P_CHK
} parser_state_t;

static parser_state_t p_state = P_WAIT_55;
static uint8_t  p_ver = 0;
static uint8_t  p_cmd = 0;
static uint16_t p_len = 0;
static uint16_t p_data_idx = 0;
static uint8_t  p_data[64];
static uint32_t p_sum = 0;

static void tuya_process_frame(uint8_t cmd, const uint8_t *data, uint16_t len) {
    g_last_heartbeat_tick = g_ticks;

    switch (cmd) {
        case TUYA_CMD_HEARTBEAT: {
            static uint8_t first_hb = 1;
            uint8_t resp = first_hb ? 0x00 : 0x00;
            first_hb = 0;
            tuya_send_frame(TUYA_CMD_HEARTBEAT, &resp, 1);
            break;
        }

        case TUYA_CMD_PROD_INFO: {
            const char prod_info[] = "{\"p\":\"wmh8266\",\"v\":\"1.0.0\",\"m\":0}";
            tuya_send_frame(TUYA_CMD_PROD_INFO, (const uint8_t *)prod_info, sizeof(prod_info) - 1);
            break;
        }

        case TUYA_CMD_WORK_MODE: {
            tuya_send_frame(TUYA_CMD_WORK_MODE, 0, 0);
            break;
        }

        case TUYA_CMD_WIFI_STATE: {
            break;
        }

        case TUYA_CMD_SET_DP: {
            if (len >= 5) {
                uint8_t dpid = data[0];
                uint8_t type = data[1];
                if (dpid == TUYA_DP_RELAY && type == TUYA_TYPE_BOOL) {
                    uint8_t new_val = data[4] ? 1 : 0;
                    relay_set(new_val);
                    buzzer_play_melody(new_val ? 1 : 2);
                    tuya_report_dp(TUYA_DP_RELAY, new_val);
                } else if (dpid == TUYA_DP_CHIME && type == TUYA_TYPE_BOOL) {
                    if (data[4]) {
                        buzzer_play_melody(0); // Westminster chime
                    }
                    tuya_report_dp(TUYA_DP_CHIME, 0); // Momentary reset
                }
            }
            break;
        }

        case TUYA_CMD_QUERY_DP: {
            tuya_report_dp(TUYA_DP_RELAY, g_relay_state);
            tuya_report_dp(TUYA_DP_CHIME, 0);
            break;
        }

        default:
            break;
    }
}

static void tuya_parse_byte(uint8_t b) {
    switch (p_state) {
        case P_WAIT_55:
            if (b == TUYA_HEADER_1) {
                p_sum = TUYA_HEADER_1;
                p_state = P_WAIT_AA;
            }
            break;

        case P_WAIT_AA:
            if (b == TUYA_HEADER_2) {
                p_sum += TUYA_HEADER_2;
                p_state = P_VER;
            } else if (b != TUYA_HEADER_1) {
                p_state = P_WAIT_55;
            }
            break;

        case P_VER:
            p_ver = b;
            p_sum += b;
            p_state = P_CMD;
            break;

        case P_CMD:
            p_cmd = b;
            p_sum += b;
            p_state = P_LEN_H;
            break;

        case P_LEN_H:
            p_len = ((uint16_t)b) << 8;
            p_sum += b;
            p_state = P_LEN_L;
            break;

        case P_LEN_L:
            p_len |= b;
            p_sum += b;
            p_data_idx = 0;
            if (p_len == 0) {
                p_state = P_CHK;
            } else if (p_len > sizeof(p_data)) {
                p_state = P_WAIT_55;
            } else {
                p_state = P_DATA;
            }
            break;

        case P_DATA:
            p_data[p_data_idx++] = b;
            p_sum += b;
            if (p_data_idx >= p_len) {
                p_state = P_CHK;
            }
            break;

        case P_CHK:
            if ((uint8_t)(p_sum & 0xFF) == b) {
                tuya_process_frame(p_cmd, p_data, p_len);
            }
            p_state = P_WAIT_55;
            break;
    }
}

/* --- Clock and Peripheral Initialization --- */
static void clock_init(void) {
    FLASH_ACR = 0x02;

    RCC_CFGR = (14 << 18) | (4 << 8);

    RCC_CR |= (1 << 24);
    while (!(RCC_CR & (1 << 25))) {}

    RCC_CFGR |= 0x02;
    while ((RCC_CFGR & 0x0C) != 0x08) {}

    STK_LOAD = 64000 - 1;
    STK_VAL  = 0;
    STK_CTRL = 0x07;

    DEMCR |= (1 << 24); // Enable TRCENA
    DWT_CTRL |= 1;      // Enable CYCCNT
}

static void gpio_init(void) {
    RCC_APB2ENR |= (1 << 2) | (1 << 3) | (1 << 0);
    RCC_APB1ENR |= (1 << 18);

    AFIO_MAPR = 0x02000000;

    uint32_t crl_a = GPIOA_CRL;
    crl_a &= ~(0xF << 4);
    crl_a |=  (0x2 << 4);
    GPIOA_CRL = crl_a;

    uint32_t crh_a = GPIOA_CRH;
    crh_a &= ~0xF00000FF;
    crh_a |=  0x20000022;
    GPIOA_CRH = crh_a;

    relay_set(0);
    GPIOA_BRR = (1 << 15);
    led_yellow_set(false);
    led_red_set(false);

    uint32_t crh_b = GPIOB_CRH;
    crh_b &= ~0x0000FF00;
    crh_b |=  0x00008B00;
    GPIOB_CRH = crh_b;
    GPIOB_BSRR = (1 << 11);

    USART3_CR1 = 0;
    USART3_BRR = 0x116;
    USART3_CR1 = (1 << 13) | (1 << 3) | (1 << 2);
}

int main(void) {
    clock_init();
    gpio_init();

    led_yellow_set(true);
    led_red_set(true);
    buzzer_play_melody(0);
    delay_ms(100);
    led_yellow_set(false);
    led_red_set(false);

    uint32_t last_led_blink = 0;
    bool led_state = false;

    while (1) {
        if (USART3_SR & (1 << 5)) {
            uint8_t byte = (uint8_t)(USART3_DR & 0xFF);
            tuya_parse_byte(byte);
        }

        if ((g_ticks - g_last_heartbeat_tick) < 15000 && g_last_heartbeat_tick > 0) {
            led_yellow_set(true);
        } else {
            if ((g_ticks - last_led_blink) >= 500) {
                last_led_blink = g_ticks;
                led_state = !led_state;
                led_yellow_set(led_state);
            }
        }
    }
}
