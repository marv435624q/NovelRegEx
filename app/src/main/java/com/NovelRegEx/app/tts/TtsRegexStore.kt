package com.NovelRegEx.app.tts

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object TtsRegexStore {
  private const val PREFS = "NovelRegEx_tts_regex"
  private const val KEY_RULES = "tts_regex_rules_v1"
  private const val KEY_NOVEL_PREFIX = "tts_regex_rules_novel_v1_"
  private const val KEY_DEFAULT_VERSION = "tts_regex_default_rules_version"
  private const val KEY_KOREAN_NUMBER = "tts_regex_korean_number_enabled_v1"
  private const val DEFAULT_RULES_VERSION = 1

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context): MutableList<TtsRegexRule> {
    ensureDefaults(context)
    return parseRules(prefs(context).getString(KEY_RULES, null)).toMutableList()
  }

  fun save(context: Context, rules: List<TtsRegexRule>) {
    prefs(context).edit { putString(KEY_RULES, rulesToJson(rules).toString()) }
  }

  fun reset(context: Context) {
    prefs(context).edit {
      putString(KEY_RULES, rulesToJson(defaultRules()).toString())
      putInt(KEY_DEFAULT_VERSION, DEFAULT_RULES_VERSION)
    }
  }

  fun loadNovel(context: Context, novelNo: String): MutableList<TtsRegexRule> =
    parseRules(prefs(context).getString(KEY_NOVEL_PREFIX + novelNo, null)).toMutableList()

  fun saveNovel(context: Context, novelNo: String, rules: List<TtsRegexRule>) {
    prefs(context).edit { putString(KEY_NOVEL_PREFIX + novelNo, rulesToJson(rules).toString()) }
  }

  fun loadEffective(context: Context, novelNo: String?): List<TtsRegexRule> =
    buildList {
      addAll(load(context))
      if (!novelNo.isNullOrBlank()) addAll(loadNovel(context, novelNo))
    }

  fun isKoreanNumberEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_KOREAN_NUMBER, true)

  fun setKoreanNumberEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit { putBoolean(KEY_KOREAN_NUMBER, enabled) }
  }

  fun exportNovelRulesJson(context: Context): JSONObject {
    val root = JSONObject()
    prefs(context).all
      .filterKeys { it.startsWith(KEY_NOVEL_PREFIX) }
      .forEach { (key, value) ->
        val novelNo = key.removePrefix(KEY_NOVEL_PREFIX)
        val raw = value as? String ?: return@forEach
        runCatching { root.put(novelNo, JSONArray(raw)) }
      }
    return root
  }

  fun importNovelRulesJson(context: Context, root: JSONObject) {
    val editor = prefs(context).edit()
    prefs(context).all.keys.filter { it.startsWith(KEY_NOVEL_PREFIX) }.forEach(editor::remove)
    val keys = root.keys()
    while (keys.hasNext()) {
      val novelNo = keys.next()
      if (!novelNo.matches(Regex("\\d+"))) continue
      val array = root.optJSONArray(novelNo) ?: continue
      editor.putString(KEY_NOVEL_PREFIX + novelNo, array.toString())
    }
    editor.apply()
  }

  private fun ensureDefaults(context: Context) {
    val p = prefs(context)
    if (!p.contains(KEY_RULES)) {
      p.edit {
        putString(KEY_RULES, rulesToJson(defaultRules()).toString())
        putInt(KEY_DEFAULT_VERSION, DEFAULT_RULES_VERSION)
      }
    }
  }

  private fun parseRules(raw: String?): List<TtsRegexRule> =
    runCatching {
      val array = JSONArray(raw ?: "[]")
      buildList {
        for (i in 0 until array.length()) add(TtsRegexRule.fromJson(array.getJSONObject(i)))
      }
    }.getOrDefault(emptyList())

  private fun rulesToJson(rules: List<TtsRegexRule>) = JSONArray().apply { rules.forEach { put(it.toJson()) } }

  private fun rule(id: String, name: String, pattern: String, replacement: String, enabled: Boolean = true) =
    TtsRegexRule(id, name, pattern, replacement, isRegex = true, enabled = enabled)

  private fun defaultRules(): List<TtsRegexRule> =
    listOf(
      rule("default-remove-zero-width-space", "제로폭 공백 제거", "[\\u200B\\u200C\\u200D\\u2060\\uFEFF]", ""),
      rule("default-normalize-nbsp", "NBSP를 일반 공백으로", "\\u00A0", " "),
      rule("default-normalize-special-spaces", "특수 공백을 일반 공백으로", "[\\u00A0\\u2007\\u202F\\u3000]+", " "),
      rule("default-normalize-linebreaks", "탭/줄바꿈을 공백으로", "[\\t\\r\\n\\u0085\\u2028\\u2029]+", " "),
      rule("default-collapse-whitespace", "연속 공백 정리", "[\\s\\p{Z}]{2,}", " "),
      rule("default-read-fraction", "분수 읽기", "(?<![0-9.,])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)\\s*/\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)(?![0-9.,])", "${'$'}{ko-number:2}분의${'$'}{ko-number:1}"),
      rule("default-read-decimal", "소수점 읽기", "(?<![0-9.$¥€])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]+)(?![0-9.A-Za-z%$¥€])", "${'$'}{ko-number:1}"),
      rule("default-remove-thousands-separator", "천 단위 쉼표 제거", "(?<=\\d),(?=\\d{3}(?:\\D|$))", ""),
      rule("default-read-percent", "퍼센트(%) 읽기", "(?<![0-9.,])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)\\s*%", "${'$'}{ko-number:1}퍼센트"),
      rule("default-read-dollar-prefix", "달러($) 앞표기 읽기", "\\$\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)", "${'$'}{ko-number:1}달러"),
      rule("default-read-dollar", "달러($) 뒤표기 읽기", "(?<![0-9.,])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)\\s*\\$", "${'$'}{ko-number:1}달러"),
      rule("default-read-yen-prefix", "엔(¥) 앞표기 읽기", "¥\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)", "${'$'}{ko-number:1}엔"),
      rule("default-read-yen", "엔(¥) 뒤표기 읽기", "(?<![0-9.,])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)\\s*¥", "${'$'}{ko-number:1}엔"),
      rule("default-read-euro-prefix", "유로(€) 앞표기 읽기", "€\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)", "${'$'}{ko-number:1}유로"),
      rule("default-read-euro", "유로(€) 뒤표기 읽기", "(?<![0-9.,])((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)\\s*€", "${'$'}{ko-number:1}유로"),
      rule("default-read-kilogram", "킬로그램(kg) 읽기", "([0-9.,]+)\\s*kg\\b", "${'$'}1 킬로그램"),
      rule("default-read-kilometer", "킬로미터(km) 읽기", "([0-9.,]+)\\s*km\\b", "${'$'}1 킬로미터"),
      rule("default-read-centimeter", "센티미터(cm) 읽기", "([0-9.,]+)\\s*cm\\b", "${'$'}1 센티미터"),
      rule("default-read-millimeter", "밀리미터(mm) 읽기", "([0-9.,]+)\\s*mm\\b", "${'$'}1 밀리미터"),
      rule("default-read-milliliter", "밀리리터(ml) 읽기", "([0-9.,]+)\\s*ml\\b", "${'$'}1 밀리리터"),
      rule("default-read-meter", "미터(m) 읽기", "([0-9.,]+)\\s*m\\b", "${'$'}1 미터"),
      rule("default-read-gram", "그램(g) 읽기", "([0-9.,]+)\\s*g\\b", "${'$'}1 그램"),
      rule("default-read-liter", "리터(l) 읽기", "([0-9.,]+)\\s*l\\b", "${'$'}1 리터"),
      rule("default-read-comma-decimal-legacy", "쉼표 소수점 읽기 (기본 꺼짐)", "([0-9]+),([0-9]+)", "${'$'}1점${'$'}2", enabled = false),
      rule("default-remove-cover-ui", "커버 접기/보기 제거", "커버\\s*(?:접기|보기)", ""),
      rule("default-remove-hanja", "한자 제거", "[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]", ""),
      rule("default-remove-long-alnum", "노벨피아 유출 식별용 긴 영숫자 제거", "[a-zA-Z0-9]{15,}", ""),
    )
}
