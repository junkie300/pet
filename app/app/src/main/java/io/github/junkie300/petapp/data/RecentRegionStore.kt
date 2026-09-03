package io.github.junkie300.petapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentRegionDataStore by preferencesDataStore(name = "recent_regions")

/**
 * 최근 선택한 지역 코드 3개 (spec.md §5.2 S-01).
 *
 * 여행 준비 중에는 같은 지역을 여러 번 다시 연다. 코드만 저장하고 이름은 매번 다시 읽는다 —
 * 행정구역 개편으로 이름이 바뀌어도 저장분이 낡지 않게 하기 위함이다.
 */
class RecentRegionStore(private val context: Context) {

    val codes: Flow<List<String>> = context.recentRegionDataStore.data.map { prefs ->
        prefs[KEY]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun remember(code: String) {
        context.recentRegionDataStore.edit { prefs ->
            val current = prefs[KEY]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(code) + current.filterNot { it == code }).take(MAX)
            prefs[KEY] = updated.joinToString(SEPARATOR)
        }
    }

    companion object {
        const val MAX = 3
        private const val SEPARATOR = ","
        private val KEY = stringPreferencesKey("codes")
    }
}
