package com.wpinrui.dovora.data.api

import org.json.JSONObject

fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
