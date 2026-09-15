package com.NovelRegEx.app.tts

import org.json.JSONObject

data class TtsRegexRule(
  val id: String,
  val name: String,
  val pattern: String,
  val replacement: String,
  val ignoreCase: Boolean = false,
  val isRegex: Boolean = true,
  val enabled: Boolean = true,
) {
  fun toJson(): JSONObject =
    JSONObject()
      .put("id", id)
      .put("name", name)
      .put("pattern", pattern)
      .put("replacement", replacement)
      .put("ignoreCase", ignoreCase)
      .put("isRegex", isRegex)
      .put("enabled", enabled)

  companion object {
    fun fromJson(json: JSONObject): TtsRegexRule =
      TtsRegexRule(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        name = json.optString("name").ifBlank { "규칙" },
        pattern = json.optString("pattern"),
        replacement = json.optString("replacement"),
        ignoreCase = json.optBoolean("ignoreCase", false),
        isRegex = json.optBoolean("isRegex", true),
        enabled = json.optBoolean("enabled", true),
      )
  }
}
