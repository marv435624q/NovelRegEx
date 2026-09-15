package com.NovelRegEx.app.tts

import java.util.regex.Matcher
import java.util.regex.Pattern

object TtsRegexEngine {
  data class CompiledRule(
    val source: TtsRegexRule,
    val pattern: Pattern,
  )

  fun compile(rules: List<TtsRegexRule>): List<CompiledRule> =
    rules.mapNotNull { rule ->
      if (!rule.enabled || rule.pattern.isEmpty()) return@mapNotNull null
      runCatching {
        val flags = if (rule.ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0
        val sourcePattern = if (rule.isRegex) rule.pattern else Pattern.quote(rule.pattern)
        CompiledRule(rule, Pattern.compile(sourcePattern, flags))
      }.getOrNull()
    }

  fun apply(
    original: String,
    rules: List<TtsRegexRule>,
    koreanNumberEnabled: Boolean = true,
  ): String = applyCompiled(original, compile(rules), koreanNumberEnabled)

  fun applyCompiled(
    original: String,
    rules: List<CompiledRule>,
    koreanNumberEnabled: Boolean = true,
  ): String {
    var result = original
    rules.forEach { compiled ->
      result =
        runCatching {
          val rule = compiled.source
          if (rule.isRegex) {
            TtsKoreanNumber.replaceAll(compiled.pattern, result, rule.replacement, koreanNumberEnabled)
          } else {
            compiled.pattern.matcher(result).replaceAll(Matcher.quoteReplacement(rule.replacement))
          }
        }.getOrElse { result }
    }
    return result
  }
}
