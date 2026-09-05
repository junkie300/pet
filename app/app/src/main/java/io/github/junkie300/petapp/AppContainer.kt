package io.github.junkie300.petapp

import android.content.Context
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.SupabaseProvider

/**
 * 수동 DI 컨테이너.
 *
 * Hilt 를 쓰지 않는다 (spec.md §1.1) — 첫 안드로이드 프로젝트에서 배울 것을
 * Kotlin/Compose 로 한정하기 위해서다. 화면이 10개를 넘으면 재검토한다.
 */
class AppContainer(context: Context) {
    val isConfigured: Boolean = SupabaseProvider.isConfigured
    val regionRepository: RegionRepository by lazy { RegionRepository(SupabaseProvider.client) }
    val placeRepository: PlaceRepository by lazy { PlaceRepository(SupabaseProvider.client) }
    val recentRegionStore: RecentRegionStore = RecentRegionStore(context.applicationContext)
}
