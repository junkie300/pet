package io.github.junkie300.petapp.ui.map

import android.content.Context
import com.kakao.vectormap.KakaoMapSdk
import io.github.junkie300.petapp.BuildConfig

/**
 * 카카오맵 SDK 초기화 한 곳 (D-69).
 *
 * 네이티브 앱 키는 Supabase anon 키와 성격이 같다 — **APK 안에 박혀서 배포되는 공개 키**이며,
 * 안전은 키를 숨겨서가 아니라 콘솔에 등록된 **패키지명 + 키 해시** 대조로 지킨다.
 * 그래서 소스에 상수로 박지 않고 `local.properties` → `BuildConfig` 라는 같은 통로를 쓴다.
 *
 * 지도가 안 되는 경우는 두 가지이고 **화면에서 서로 다른 말을 해야 한다**:
 * - 키가 없다 ([isConfigured] == false) — 빌드는 되고 지도만 안내로 바뀐다 (spec.md §1.2)
 * - SDK 를 못 올렸다 ([startupError] != null) — 이 기기에 맞는 네이티브 라이브러리가 없다 (D-71)
 */
object KakaoMapProvider {

    val isConfigured: Boolean = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()

    /** SDK 초기화가 실패한 이유. null 이면 지도를 열어도 된다. */
    var startupError: String? = null
        private set

    val isUsable: Boolean get() = isConfigured && startupError == null

    /**
     * 앱이 켜질 때 한 번 부른다. SDK 는 지도를 그리기 전에 초기화돼 있어야 한다.
     *
     * ⚠️ **실패해도 앱을 죽이지 않는다.** `KakaoMapSdk.init` 은 네이티브 라이브러리를 여는데,
     * 카카오가 **ARM 빌드만 배포한다** — x86_64 기기(에뮬레이터 대부분)에서는 여기서
     * `UnsatisfiedLinkError` 가 난다. 그대로 두면 지도와 상관없는 화면까지 전부 못 쓰게 된다 (D-71).
     *
     * ⚠️ 키가 **틀리거나** 콘솔 등록이 어긋난 것은 여기서 드러나지 않는다. 그건 지도를
     * 띄우는 순간 `MapLifeCycleCallback.onMapError` 로 온다 — [MapScreen] 이 그걸 받아
     * 검은 화면 대신 이유를 적는다 (D-69 의 함정).
     */
    fun initialize(context: Context) {
        if (!isConfigured) return
        runCatching { KakaoMapSdk.init(context.applicationContext, BuildConfig.KAKAO_NATIVE_APP_KEY) }
            .onFailure { error -> startupError = error.message ?: error.javaClass.simpleName }
    }
}
