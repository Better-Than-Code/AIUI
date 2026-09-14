package com.cellular.rpc.engine

import android.content.Context
import android.util.Log
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier
import java.io.IOException

/**
 * Sprint 6.4: The Router (Intent Classification via TFLite)
 * 
 * Uses an ultra-micro (<1MB) quantized text classification model.
 * If the user's intent is purely a local UI command (e.g. "dark mode"),
 * we execute it instantly on the edge and cancel the SMS transmission.
 * If it requires external knowledge (e.g. "what is the weather"), we route it to the Gateway.
 */
class CellularIntentRouter(private val context: Context) {

    private var classifier: NLClassifier? = null
    
    // We will use a mock threshold if the model fails to load,
    // ensuring the architecture still works gracefully.
    private var isMockMode = false

    init {
        try {
            // Note: In a real prod environment, this model file would be bundled in assets/
            // We use a safe try-catch here to simulate the edge routing logic without crashing
            val options = NLClassifier.NLClassifierOptions.builder().build()
            classifier = NLClassifier.createFromFileAndOptions(context, "intent_classifier.tflite", options)
            Log.i("CellularIntentRouter", "TFLite Model loaded successfully.")
        } catch (e: IOException) {
            Log.w("CellularIntentRouter", "TFLite model not found in assets, falling back to heuristic mock mode.")
            isMockMode = true
        }
    }

    /**
     * Epic 13.3: Strict Slash-Command Parser (/)
     * Enforce strict prefix matching (/theme, /widget, /net, /safe, /clear, /reset).
     * Route all non-prefixed conversational text strictly as chat data across cellular transport.
     * 
     * @return true if the intent is strictly a LOCAL slash command that requires no SMS overhead.
     */
    fun isLocalUiIntent(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (!trimmed.startsWith("/")) {
            // Conversational text is NEVER swallowed locally
            return false
        }

        return OfflineCommandRouter.isSlashCommand(trimmed)
    }
}
