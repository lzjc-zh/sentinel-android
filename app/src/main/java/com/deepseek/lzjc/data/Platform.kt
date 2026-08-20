package com.deepseek.lzjc.data

/**
 * Supported AI platforms.
 */
enum class Platform(val displayName: String, val key: String) {
    DEEPSEEK("DeepSeek", "deepseek"),
    MIMO("MiMo", "mimo");

    companion object {
        fun fromKey(key: String): Platform =
            entries.find { it.key == key } ?: DEEPSEEK
    }
}
