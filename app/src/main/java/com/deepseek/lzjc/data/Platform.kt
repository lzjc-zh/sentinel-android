package com.deepseek.lzjc.data

/**
 * Supported AI platforms.
 */
enum class Platform(val displayName: String, val key: String) {
    DEEPSEEK("DeepSeek", "deepseek"),
    MIMO("MiMo", "mimo"),
    ARK("火山方舟", "ark"),
    GLM("智谱GLM", "glm"),
    MINIMAX("MiniMax", "minimax");

    companion object {
        fun fromKey(key: String): Platform =
            entries.find { it.key == key } ?: DEEPSEEK

        const val PREF_KEY = "current_platform"
        const val PREF_FIRST_LAUNCH = "first_launch_done"
    }
}
