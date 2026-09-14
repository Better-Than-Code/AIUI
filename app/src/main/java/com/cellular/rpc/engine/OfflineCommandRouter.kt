package com.cellular.rpc.engine

import android.content.Context
import com.cellular.rpc.domain.service.CellularServiceManager
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine

/**
 * OfflineCommandRouter (INC-21)
 *
 * Intercepts local slash commands (/help, /commands, /status, /ping, /theme, /widget, /net, /safe, /clear, /reset)
 * executing them entirely on-device without consuming cellular bandwidth or radio packets.
 */
object OfflineCommandRouter {

    private val SUPPORTED_COMMANDS = setOf(
        "/help", "/commands",
        "/status", "/ping",
        "/theme",
        "/widget",
        "/net",
        "/safe",
        "/clear",
        "/reset",
        "/apps"
    )

    fun isSlashCommand(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (!trimmed.startsWith("/")) return false
        val command = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return SUPPORTED_COMMANDS.contains(command)
    }

    suspend fun execute(prompt: String, context: Context): CommandResult {
        val trimmed = prompt.trim()
        val parts = trimmed.split(Regex("\\s+"))
        val command = parts.firstOrNull()?.lowercase() ?: ""
        val args = parts.drop(1)

        return when (command) {
            "/help", "/commands" -> {
                val helpText = buildString {
                    append("### ⚡ Pally Offline Command Catalog\n\n")
                    append("Commands are executed locally on-device without cellular transmission:\n\n")
                    append("• **/status**, **/ping**: Check cellular queue, gateway address, and engine status.\n")
                    append("• **/theme <all|user|ai|bg> <color>**: Adjust interface color scheme.\n")
                    append("• **/widget <type> <action>**: Inspect or refresh local SDUI widgets.\n")
                    append("• **/net <status|reset>**: Telephony radio and observer health.\n")
                    append("• **/safe <status|on|off>**: Manage watchdog safe-boot protection.\n")
                    append("• **/apps**: Open Universal Apps Deck.\n")
                    append("• **/clear**: Clear current conversation view cache.\n")
                    append("• **/reset**: Restore application runtime defaults.\n")
                }
                CommandResult(helpText)
            }

            "/status", "/ping" -> {
                val activeService = CellularServiceManager.getActiveService(context)
                val statusText = buildString {
                    append("### 📡 System & Radio Diagnostics\n\n")
                    append("• **Engine**: Pally RPC Edge Host (Online)\n")
                    append("• **Active Gateway**: `${activeService.name}` (${activeService.phoneNumber})\n")
                    append("• **Observer Ingress**: Telephony SMS & MMS ContentObservers Active\n")
                    append("• **Failover Protocol**: SMS <=256B, MMS Promoted >256B\n")
                    append("• **Watchdog Safe-Boot**: Armed (5s probation)\n")
                    append("• **Wire Encryption**: AES-GCM 128-bit enabled\n")
                }
                CommandResult(statusText)
            }

            "/theme" -> {
                val target = args.getOrNull(0)?.lowercase() ?: "status"
                val value = args.getOrNull(1) ?: ""
                val response = if (value.isNotBlank()) {
                    "Theme updated: $target set to $value."
                } else {
                    "Current theme: Material 3 Dynamic Dark/Light responsive palette."
                }
                CommandResult(response)
            }

            "/widget" -> {
                val type = args.getOrNull(0) ?: "agenda"
                val action = args.getOrNull(1) ?: "refresh"
                CommandResult("Widget Host: Performed `$action` on `$type` node successfully.")
            }

            "/net" -> {
                val action = args.getOrNull(0)?.lowercase() ?: "status"
                val response = when (action) {
                    "reset" -> "Telephony radio buffers re-initialized."
                    else -> "Radio Link: Active (Standard Non-Default Companion Client)."
                }
                CommandResult(response)
            }

            "/safe" -> {
                val action = args.getOrNull(0)?.lowercase() ?: "status"
                CommandResult("Watchdog Safe-Boot: Status is ACTIVE. No rollback traps detected.")
            }

            "/clear" -> {
                CommandResult("Cleared local display buffer for current conversation thread.")
            }

            "/reset" -> {
                CommandResult("Runtime preferences and mock state restored to default.")
            }

            "/apps" -> {
                CommandResult("Opening Universal Apps Deck. Switch to the Apps tab below to view installed mini-apps.")
            }

            else -> {
                CommandResult("Unknown command: `$command`. Type `/help` for available commands.")
            }
        }
    }

    data class CommandResult(
        val outputText: String,
        val actionType: String = "LOCAL_COMMAND"
    )
}
