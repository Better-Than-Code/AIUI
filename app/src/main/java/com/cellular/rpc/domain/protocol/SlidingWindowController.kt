package com.cellular.rpc.domain.protocol

import java.util.BitSet

class SlidingWindowController(
    val windowSize: Int = 4,
    val maxSequence: Int = 65535
) {
    var baseSequence: Int = 0
        private set
    var nextSequence: Int = 0
        private set

    private val unacknowledgedFrames = mutableMapOf<Int, Frame>()
    private val rxBufferedFrames = mutableMapOf<Int, Frame>()

    @Synchronized
    fun canTransmit(): Boolean {
        val currentInFlight = (nextSequence - baseSequence + maxSequence) % maxSequence
        return currentInFlight < windowSize
    }

    @Synchronized
    fun getInFlightCount(): Int {
        return (nextSequence - baseSequence + maxSequence) % maxSequence
    }

    @Synchronized
    fun getInFlightFrames(): List<Frame> {
        return unacknowledgedFrames.values.toList()
    }

    @Synchronized
    fun registerOutbound(frame: Frame): Int {
        val seq = nextSequence
        unacknowledgedFrames[seq] = frame
        nextSequence = (nextSequence + 1) % maxSequence
        return seq
    }

    /**
     * Process an incoming selective-repeat ACK.
     * @param ackBase The highest cumulative sequence confirmed.
     * @param bitmask 32-bit field where bit 'i' acknowledges (ackBase + i + 1).
     */
    @Synchronized
    fun processAck(ackBase: Int, bitmask: Long): List<Frame> {
        val newlyAcked = mutableListOf<Frame>()

        // 1. Advance the cumulative base window
        while (baseSequence != ackBase && unacknowledgedFrames.containsKey(baseSequence)) {
            unacknowledgedFrames.remove(baseSequence)?.let { newlyAcked.add(it) }
            baseSequence = (baseSequence + 1) % maxSequence
        }

        // 2. Clear selectively ACKed items based on the bitmask
        val bits = BitSet.valueOf(longArrayOf(bitmask))
        for (i in 0 until 32) {
            if (bits.get(i)) {
                val targetSeq = (ackBase + 1 + i) % maxSequence
                unacknowledgedFrames.remove(targetSeq)?.let { newlyAcked.add(it) }
            }
        }

        return newlyAcked
    }

    @Synchronized
    fun getFramesRequiringRetry(timeoutMs: Long, currentTimeMs: Long): List<Frame> {
        // Collect frames that have exceeded their transmission deadline
        return unacknowledgedFrames.values.filter { frame ->
            (currentTimeMs - frame.timestampMs) >= timeoutMs
        }
    }

    @Synchronized
    fun processInbound(frame: Frame): InboundResult {
        val seq = frame.seqNo and 0xFFFF
        
        // Discard duplicates behind the base window
        if (seq < baseSequence && (baseSequence - seq) < (maxSequence / 2)) {
            return InboundResult.Duplicate(calculateAckBase(), calculateBitmask())
        }

        rxBufferedFrames[seq] = frame

        // Check if contiguous frames can now be delivered to the application
        val deliverable = mutableListOf<Frame>()
        while (rxBufferedFrames.containsKey(baseSequence)) {
            deliverable.add(rxBufferedFrames.remove(baseSequence)!!)
            baseSequence = (baseSequence + 1) % maxSequence
        }

        return InboundResult.Deliver(
            frames = deliverable,
            ackBase = baseSequence,
            ackBitmask = calculateBitmask()
        )
    }

    @Synchronized
    fun reset(initialSeq: Int = 0) {
        baseSequence = initialSeq
        nextSequence = initialSeq
        unacknowledgedFrames.clear()
        rxBufferedFrames.clear()
    }

    private fun calculateAckBase(): Int = baseSequence

    private fun calculateBitmask(): Long {
        val bitset = BitSet(32)
        for (i in 0 until 32) {
            val target = (baseSequence + 1 + i) % maxSequence
            if (rxBufferedFrames.containsKey(target)) {
                bitset.set(i)
            }
        }
        val longs = bitset.toLongArray()
        return if (longs.isNotEmpty()) longs[0] else 0L
    }

    sealed class InboundResult {
        data class Deliver(val frames: List<Frame>, val ackBase: Int, val ackBitmask: Long) : InboundResult()
        data class Duplicate(val ackBase: Int, val ackBitmask: Long) : InboundResult()
    }
}
