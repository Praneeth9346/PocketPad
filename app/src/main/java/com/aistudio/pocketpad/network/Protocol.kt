package com.aistudio.pocketpad.network

object Protocol {
    const val VERSION = 1

    const val KEEPALIVE: Byte = 0x00
    const val STEER: Byte = 0x01
    const val PEDALS: Byte = 0x02
    const val BUTTON: Byte = 0x03
    const val SNAPSHOT: Byte = 0x04
    const val LEFT_STICK: Byte = 0x05
    const val RIGHT_STICK: Byte = 0x06
    const val MOUSE: Byte = 0x07
    const val MEDIA: Byte = 0x08
    const val PING: Byte = 0x09
    const val PONG: Byte = 0x0A
    const val RUMBLE: Byte = 0x0B
    const val TELEMETRY: Byte = 0x10
    const val LATENCY_PROBE: Byte = 0x20

    const val TELEMETRY_PACKET_SIZE = 13
}
