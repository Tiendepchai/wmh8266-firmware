    .syntax unified
    .cpu cortex-m3
    .thumb

    .global g_pfnVectors
    .global Reset_Handler
    .global Default_Handler

    .section .isr_vector, "a", %progbits
    .type g_pfnVectors, %object
g_pfnVectors:
    .word   _estack
    .word   Reset_Handler
    .word   Default_Handler
    .word   Default_Handler
    .word   Default_Handler
    .word   Default_Handler
    .word   Default_Handler
    .word   0
    .word   0
    .word   0
    .word   0
    .word   Default_Handler
    .word   Default_Handler
    .word   0
    .word   Default_Handler
    .word   SysTick_Handler
    .size g_pfnVectors, .-g_pfnVectors

    .section .text.Reset_Handler
    .type Reset_Handler, %function
Reset_Handler:
    ldr     r0, =_sdata
    ldr     r1, =_edata
    ldr     r2, =_sidata
    movs    r3, #0
    b       LoopCopyDataInit

CopyDataInit:
    ldr     r4, [r2, r3]
    str     r4, [r0, r3]
    adds    r3, r3, #4

LoopCopyDataInit:
    adds    r4, r0, r3
    cmp     r4, r1
    bcc     CopyDataInit

    ldr     r2, =_sbss
    ldr     r4, =_ebss
    movs    r3, #0
    b       LoopFillZerobss

FillZerobss:
    str     r3, [r2]
    adds    r2, r2, #4

LoopFillZerobss:
    cmp     r2, r4
    bcc     FillZerobss

    bl      main
    b       .
    .size Reset_Handler, .-Reset_Handler

    .section .text.DefaultHandler
    .type Default_Handler, %function
Default_Handler:
    b       .
    .size Default_Handler, .-Default_Handler
