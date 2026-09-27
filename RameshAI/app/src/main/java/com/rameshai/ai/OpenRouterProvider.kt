package com.rameshai.ai

import com.rameshai.config.RuntimeConfig

/**
 * OpenRouter exposes an OpenAI-shaped /chat/completions API, so it reuses
 * [OpenAICompatibleProvider] wholesale with OpenRouter's base URL as the default.
 * Kept as its own class (rather than just picking the OpenAI provider) so the
 * factory, Settings screen, and logs can distinguish it clearly, and so it can
 * diverge later (e.g. OpenRouter-specific headers/model routing) without touching
 * the OpenAI implementation.
 */
class OpenRouterProvider(config: RuntimeConfig) : AIProvider by OpenAICompatibleProvider(
    config.copy(aiBaseUrl = config.aiBaseUrl.ifBlank { "https://openrouter.ai/api/v1" })
) {
    override val id: String = "openrouter"
}
