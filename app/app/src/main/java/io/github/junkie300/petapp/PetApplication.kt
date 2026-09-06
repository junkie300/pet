package io.github.junkie300.petapp

import android.app.Application
import io.github.junkie300.petapp.ui.map.KakaoMapProvider

class PetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 지도를 그리기 전에 한 번 초기화돼 있어야 한다. 키가 없으면 아무 일도 하지 않는다 (D-69).
        KakaoMapProvider.initialize(this)
    }
}
