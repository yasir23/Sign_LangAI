/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.signlanguage

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for SignLanguage translation screen
 * Manages the UI state and coordinates between detection and translation engines
 */
@HiltViewModel
class SignLanguageViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(SignLanguageUiState())
    val uiState: StateFlow<SignLanguageUiState> = _uiState.asStateFlow()
    
    private var signDetector: SignLanguageDetector? = null
    private var translationEngine: SignTranslationEngine? = null
    
    private var frameCount = 0
    private var lastFpsUpdate = System.currentTimeMillis()
    
    /**
     * Initialize the sign language model and detection engine
     */
    fun initializeModel(context: Context, modelName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isProcessing = true)
                
                // Initialize translation engine
                translationEngine = SignTranslationEngine()
                translationEngine?.initialize(context, modelName)
                
                // Initialize sign detector
                signDetector = SignLanguageDetector(context)
                signDetector?.onSignDetected = { features ->
                    processDetectedSign(features)
                }
                
                _uiState.value = _uiState.value.copy(
                    isModelInitialized = true,
                    isProcessing = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Failed to initialize: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Analyze a camera frame for sign language detection
     */
    fun analyzeFrame(imageProxy: ImageProxy) {
        if (!_uiState.value.isModelInitialized) {
            imageProxy.close()
            return
        }
        
        // Update FPS counter
        updateFps()
        
        // Process frame with detector
        signDetector?.analyzeFrame(imageProxy)
    }
    
    private fun processDetectedSign(features: FloatArray) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isProcessing = true)
                
                val startTime = System.currentTimeMillis()
                
                // Translate sign using AI engine
                val translation = translationEngine?.translateSign(features) ?: "Unknown sign"
                val confidence = calculateConfidence(features)
                val latency = System.currentTimeMillis() - startTime
                
                // Add to conversation history
                val message = ConversationMessage(
                    text = translation,
                    confidence = confidence,
                    timestamp = getCurrentTime()
                )
                
                val updatedHistory = _uiState.value.conversationHistory.toMutableList()
                updatedHistory.add(0, message) // Add to beginning
                
                // Keep only recent messages (max 20)
                if (updatedHistory.size > 20) {
                    updatedHistory.removeAt(updatedHistory.size - 1)
                }
                
                _uiState.value = _uiState.value.copy(
                    currentTranslation = translation,
                    confidence = confidence,
                    latency = latency,
                    conversationHistory = updatedHistory,
                    isProcessing = false,
                    currentHandLandmarks = listOf() // Simulate hand landmarks
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Translation failed: ${e.message}"
                )
            }
        }
    }
    
    private fun calculateConfidence(features: FloatArray): Float {
        // Simple confidence calculation based on feature stability
        val variance = features.map { it * it }.average().toFloat()
        return (1.0f - (variance / 100f)).coerceIn(0.6f, 0.95f)
    }
    
    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastFpsUpdate >= 1000) { // Update every second
            val fps = (frameCount * 1000f / (currentTime - lastFpsUpdate)).toInt()
            _uiState.value = _uiState.value.copy(fps = fps)
            
            frameCount = 0
            lastFpsUpdate = currentTime
        }
    }
    
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    
    override fun onCleared() {
        super.onCleared()
        signDetector?.close()
        translationEngine?.cleanup()
    }
}

/**
 * UI state for the SignLanguage screen
 */
data class SignLanguageUiState(
    val isModelInitialized: Boolean = false,
    val isProcessing: Boolean = false,
    val currentTranslation: String = "",
    val confidence: Float = 0f,
    val fps: Int = 0,
    val latency: Long = 0L,
    val currentHandLandmarks: List<Any> = emptyList(),
    val conversationHistory: List<ConversationMessage> = emptyList(),
    val errorMessage: String = ""
)

/**
 * Data class for conversation messages
 */
data class ConversationMessage(
    val text: String,
    val confidence: Float,
    val timestamp: String
)
