package com.example.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class GestureType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    TWO_FINGER_TAP,
    TWO_FINGER_SWIPE_UP,
    TWO_FINGER_SWIPE_DOWN
}

enum class GestureAction(val labelAr: String) {
    NOTHING("لا شيء"),
    TOGGLE_TOOLBAR("إظهار/إخفاء شريط الأدوات"),
    NEXT_PAGE("الصفحة التالية"),
    PREV_PAGE("الصفحة السابقة"),
    ZOOM_IN("تكبير"),
    ZOOM_OUT("تصغير"),
    TOGGLE_NIGHT_MODE("تبديل الوضع الليلي"),
    ADD_BOOKMARK("إضافة إشارة مرجعية"),
    OPEN_TOC("فتح الفهرس"),
    OPEN_SEARCH("فتح البحث"),
    SCROLL_TO_TOP("الانتقال للأعلى"),
    SCROLL_TO_BOTTOM("الانتقال للأسفل"),
    TOGGLE_ANNOTATION("تفعيل/إيقاف التعديل")
}

val defaultGestures = mapOf(
    GestureType.SINGLE_TAP           to GestureAction.TOGGLE_TOOLBAR,
    GestureType.DOUBLE_TAP           to GestureAction.ZOOM_IN,
    GestureType.LONG_PRESS           to GestureAction.ADD_BOOKMARK,
    GestureType.SWIPE_LEFT           to GestureAction.NEXT_PAGE,
    GestureType.SWIPE_RIGHT          to GestureAction.PREV_PAGE,
    GestureType.SWIPE_UP             to GestureAction.NOTHING,
    GestureType.SWIPE_DOWN           to GestureAction.NOTHING,
    GestureType.TWO_FINGER_TAP       to GestureAction.TOGGLE_NIGHT_MODE,
    GestureType.TWO_FINGER_SWIPE_UP  to GestureAction.SCROLL_TO_TOP,
    GestureType.TWO_FINGER_SWIPE_DOWN to GestureAction.SCROLL_TO_BOTTOM
)

object GestureSerializer {
    fun serialize(mappings: Map<GestureType, GestureAction>): String {
        val sb = StringBuilder()
        sb.append("{")
        mappings.entries.forEachIndexed { index, entry ->
            sb.append("\"").append(entry.key.name).append("\":\"").append(entry.value.name).append("\"")
            if (index < mappings.size - 1) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }

    fun deserialize(jsonStr: String?): Map<GestureType, GestureAction> {
        if (jsonStr.isNullOrBlank()) return defaultGestures
        val map = defaultGestures.toMutableMap()
        try {
            val element = Json.parseToJsonElement(jsonStr)
            element.jsonObject.forEach { (keyStr, jsonElement) ->
                try {
                    val key = GestureType.valueOf(keyStr)
                    val valStr = jsonElement.jsonPrimitive.content
                    val value = GestureAction.valueOf(valStr)
                    map[key] = value
                } catch (e: Exception) {
                    // Ignore invalid keys or values
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureSerializer", "Failed to deserialize gestures", e)
            return defaultGestures
        }
        return map
    }
}
