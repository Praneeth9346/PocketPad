package com.aistudio.pocketpad

import com.aistudio.pocketpad.network.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProtocolEncodingTest {

    @Test
    fun testSteeringPacketEncoding() {
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Protocol.STEER)
        buffer.putShort(32767.toShort())

        val bytes = buffer.array()
        assertEquals(3, bytes.size)
        assertEquals(Protocol.STEER, bytes[0])

        // Verify little-endian wire representation 0x01, 0xFF, 0x7F
        assertArrayEquals(byteArrayOf(0x01, 0xFF.toByte(), 0x7F.toByte()), bytes)
    }

    @Test
    fun testPedalsPacketEncoding() {
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Protocol.PEDALS)
        buffer.put(255.toByte()) // Brake
        buffer.put(128.toByte()) // Throttle

        val bytes = buffer.array()
        assertEquals(3, bytes.size)
        assertEquals(Protocol.PEDALS, bytes[0])
        assertEquals(255.toByte(), bytes[1])
        assertEquals(128.toByte(), bytes[2])
    }

    @Test
    fun testButtonPacketEncoding() {
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Protocol.BUTTON)
        buffer.put(0.toByte()) // Button A
        buffer.put(1.toByte()) // Pressed

        val bytes = buffer.array()
        assertEquals(3, bytes.size)
        assertArrayEquals(byteArrayOf(0x03, 0x00, 0x01), bytes)
    }

    @Test
    fun testKeepalivePacketEncoding() {
        val buffer = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(Protocol.KEEPALIVE)

        val bytes = buffer.array()
        assertEquals(1, bytes.size)
        assertEquals(Protocol.KEEPALIVE, bytes[0])
    }
}
