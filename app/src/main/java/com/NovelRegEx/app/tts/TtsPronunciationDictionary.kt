package com.NovelRegEx.app.tts

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

object TtsPronunciationDictionary {
  private const val KEY_GLOBAL = "tts_pronunciation_dictionary_global"
  private const val KEY_NOVEL_PREFIX = "tts_pronunciation_dictionary_novel_"

  data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val spoken: String,
    val enabled: Boolean = true,
  ) {
    fun toJson(): JSONObject =
      JSONObject()
        .put("id", id)
        .put("source", source)
        .put("spoken", spoken)
        .put("enabled", enabled)

    companion object {
      fun fromJson(json: JSONObject): Rule? {
        val source = json.optString("source").trim()
        val spoken = json.optString("spoken").trim()
        if (source.isEmpty() || spoken.isEmpty()) return null
        return Rule(
          id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
          source = source,
          spoken = spoken,
          enabled = json.optBoolean("enabled", true),
        )
      }
    }
  }

  private data class CachedRules(
    val raw: String,
    val rules: List<Rule>,
  )

  private val parsedCache = ConcurrentHashMap<String, CachedRules>()

  fun getRules(
    context: Context,
    novelNo: String? = null,
  ): List<Rule> {
    val key = storageKey(novelNo) ?: return emptyList()
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val raw = prefs.getString(key, "").orEmpty()
    parsedCache[key]?.takeIf { it.raw == raw }?.let { return it.rules }

    val parsed = parseStored(raw)
    val normalizedRaw = encodeRules(parsed)

    if (raw.isNotBlank() && raw.trimStart().firstOrNull() != '[') {
      prefs.edit { putString(key, normalizedRaw) }
    }
    parsedCache[key] = CachedRules(normalizedRaw, parsed)
    return parsed
  }

  fun saveRules(
    context: Context,
    novelNo: String? = null,
    rules: List<Rule>,
  ) {
    val key = storageKey(novelNo) ?: return
    validateRules(rules)
    val raw = encodeRules(rules)
    PreferenceManager.getDefaultSharedPreferences(context).edit {
      putString(key, raw)
    }
    parsedCache[key] = CachedRules(raw, rules.toList())
  }

  fun apply(
    context: Context,
    text: String,
    novelNo: String?,
  ): String {
    if (text.isEmpty()) return text

    val replacements = LinkedHashMap<String, String>()
    getRules(context)
      .asSequence()
      .filter { it.enabled }
      .forEach { replacements[it.source] = it.spoken }

    if (!novelNo.isNullOrBlank()) {
      getRules(context, novelNo)
        .asSequence()
        .filter { it.enabled }
        .forEach { replacements[it.source] = it.spoken }
    }

    if (replacements.isEmpty()) return text

    val sources = replacements.keys.sortedByDescending { it.length }
    val matcher =
      Pattern
        .compile(sources.joinToString("|") { Pattern.quote(it) })
        .matcher(text)

    val result = StringBuffer()
    while (matcher.find()) {
      matcher.appendReplacement(
        result,
        Matcher.quoteReplacement(replacements[matcher.group()].orEmpty()),
      )
    }
    matcher.appendTail(result)
    return result.toString()
  }

  fun exportJson(context: Context): JSONObject {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val novels = JSONObject()

    prefs.all.keys
      .asSequence()
      .filter { it.startsWith(KEY_NOVEL_PREFIX) }
      .map { it.removePrefix(KEY_NOVEL_PREFIX) }
      .filter { it.matches(Regex("""\d+""")) }
      .forEach { novelNo ->
        novels.put(novelNo, rulesToJsonArray(getRules(context, novelNo)))
      }

    return JSONObject()
      .put("format", 2)
      .put("global", rulesToJsonArray(getRules(context)))
      .put("novels", novels)
  }

  fun importJson(
    context: Context,
    json: JSONObject,
  ) {
    val global = parseJsonValue(json.opt("global"))
    val novelsObject = json.optJSONObject("novels") ?: JSONObject()
    val novels = linkedMapOf<String, List<Rule>>()

    val keys = novelsObject.keys()
    while (keys.hasNext()) {
      val novelNo = keys.next()
      if (!novelNo.matches(Regex("""\d+"""))) continue
      novels[novelNo] = parseJsonValue(novelsObject.opt(novelNo))
    }

    validateRules(global)
    novels.values.forEach(::validateRules)

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit {
      prefs.all.keys
        .filter { it.startsWith(KEY_NOVEL_PREFIX) }
        .forEach { remove(it) }

      putString(KEY_GLOBAL, encodeRules(global))
      novels.forEach { (novelNo, rules) ->
        putString(KEY_NOVEL_PREFIX + novelNo, encodeRules(rules))
      }
    }
    parsedCache.clear()
  }

  private fun storageKey(novelNo: String?): String? =
    when {
      novelNo == null -> KEY_GLOBAL
      novelNo.matches(Regex("""\d+""")) -> KEY_NOVEL_PREFIX + novelNo
      else -> null
    }

  private fun validateRules(rules: List<Rule>) {
    val seen = hashSetOf<String>()
    rules.forEachIndexed { index, rule ->
      require(rule.source.isNotBlank()) { "${index + 1}번째 규칙의 원문이 비어 있습니다." }
      require(rule.spoken.isNotBlank()) { "${index + 1}번째 규칙의 읽는 법이 비어 있습니다." }
      require(seen.add(rule.source)) { "같은 원문 '${rule.source}'이(가) 중복되어 있습니다." }
    }
  }

  private fun parseStored(raw: String): List<Rule> {
    val value = raw.trim()
    if (value.isEmpty()) return emptyList()

    if (value.startsWith("[")) {
      return runCatching {
        val array = JSONArray(value)
        buildList {
          for (index in 0 until array.length()) {
            Rule.fromJson(array.optJSONObject(index) ?: continue)?.let(::add)
          }
        }
      }.getOrElse { emptyList() }
    }

    return buildList {
      value.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val separator = line.indexOf('=')
        if (separator <= 0) return@forEach
        val source = line.substring(0, separator).trim()
        val spoken = line.substring(separator + 1).trim()
        if (source.isNotEmpty() && spoken.isNotEmpty()) {
          add(Rule(source = source, spoken = spoken))
        }
      }
    }.distinctBy { it.source }
  }

  private fun parseJsonValue(value: Any?): List<Rule> =
    when (value) {
      is JSONArray ->
        buildList {
          for (index in 0 until value.length()) {
            Rule.fromJson(value.optJSONObject(index) ?: continue)?.let(::add)
          }
        }
      is String -> parseStored(value)
      else -> emptyList()
    }

  private fun rulesToJsonArray(rules: List<Rule>): JSONArray =
    JSONArray().apply {
      rules.forEach { put(it.toJson()) }
    }

  private fun encodeRules(rules: List<Rule>): String =
    rulesToJsonArray(rules).toString()
}
