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
     * @return true if the intent is strictly a LOCAL action that requires no SMS overhead.
     */
    fun isLocalUiIntent(prompt: String): Boolean {
        if (isMockMode || classifier == null) {
            return isLocalUiIntentMock(prompt)
        }

        try {
            val results = classifier!!.classify(prompt)
            val localScore = results.firstOrNull { it.label == "LOCAL_UI_CHANGE" }?.score ?: 0f
            return localScore > 0.85f
        } catch (e: Exception) {
            Log.e("CellularIntentRouter", "TFLite classification failed: ${e.message}")
            return isLocalUiIntentMock(prompt)
        }
    }

    private fun isLocalUiIntentMock(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("dark mode") || 
               lower.contains("light mode") || 
               lower.contains("increase font") || 
               lower.contains("bigger text") ||
               lower.contains("clear cache")
    }
}
