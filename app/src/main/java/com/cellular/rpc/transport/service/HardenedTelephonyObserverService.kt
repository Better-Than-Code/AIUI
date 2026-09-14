package com.cellular.rpc.transport.service

import android.content.Context
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage

data class SmsRow(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long
)

object HardenedTelephonyObserverService {

    suspend fun processSmsRows(context: Context, rows: List<SmsRow>) {
        if (rows.isEmpty()) return

        // Cluster multi-part SMS by address and 4000ms window
        val clusters = rows.groupBy { row ->
            val cleanAddr = row.address.filter { it.isDigit() || it == '+' }
            "$cleanAddr:${row.date / 4000L}"
        }

        for ((_, cluster) in clusters) {
            val ordered = cluster.sortedBy { it.id }
            val fullBody = ordered.joinToString("") { it.body }
            val sender = ordered.first().address

            val inbound = InboundCellularMessage(
                transportType = CellularTransportType.SMS_TEXT_WIRE,
                senderAddress = sender,
                rawText = fullBody
            )
            CellularMessageDispatcher.dispatchInbound(context, inbound)
        }
    }
}
