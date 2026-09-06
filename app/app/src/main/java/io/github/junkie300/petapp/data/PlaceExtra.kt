package io.github.junkie300.petapp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * `places.extra` 를 로컬 DB(SQLite)에 담고 꺼내는 두 줄.
 *
 * 소스마다 키가 달라 컬럼으로 펴지 않는다 ([Place.extra] 와 같은 이유). **원문 JSON 문자열
 * 그대로** 넣어 두고 읽을 때 되돌린다. 즐겨찾기 스냅샷과 오프라인 캐시가 같이 쓴다.
 */
internal object PlaceExtra {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(extra: JsonObject?): String? = extra?.toString()

    /** 깨진 값이 들어 있으면 null 로 떨어진다 — 캐시 한 줄 때문에 화면이 죽지 않는다. */
    fun decode(text: String?): JsonObject? =
        runCatching { text?.let { json.parseToJsonElement(it).jsonObject } }.getOrNull()
}
