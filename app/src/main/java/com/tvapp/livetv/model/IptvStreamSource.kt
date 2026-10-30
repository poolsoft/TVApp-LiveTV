package com.tvapp.livetv.model

import org.json.JSONArray
import org.json.JSONObject

data class IptvStreamSource(
    val label: String,
    val url: String,
) {
    companion object {
        fun toJson(sources: List<IptvStreamSource>): String? {
            if (sources.isEmpty()) return null
            val array = JSONArray()
            for (source in sources) {
                val obj = JSONObject()
                obj.put("label", source.label)
                obj.put("url", source.url)
                array.put(obj)
            }
            return array.toString()
        }

        fun fromJson(jsonStr: String?): List<IptvStreamSource> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<IptvStreamSource>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val label = obj.optString("label", "Kaynak ${i + 1}")
                    val url = obj.optString("url", "")
                    if (url.isNotBlank()) {
                        list.add(IptvStreamSource(label = label, url = url))
                    }
                }
            } catch (_: Exception) {
            }
            return list
        }
    }
}

