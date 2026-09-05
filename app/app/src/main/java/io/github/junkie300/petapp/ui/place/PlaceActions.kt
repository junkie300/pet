package io.github.junkie300.petapp.ui.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Place

/**
 * 상세 화면의 바깥 앱 연동 (spec.md §5.2 S-03 — 길찾기 · 전화 · 공유).
 *
 * 세 가지 모두 **키가 필요 없다.** 카카오맵 SDK(네이티브 앱 키)는 지도를 앱 안에 그릴 때
 * 필요한 것이고, 길찾기는 카카오맵의 공개 링크로 넘기면 된다 (D-57).
 */
object PlaceActions {

    /** `ACTION_DIAL` 은 권한이 필요 없다. 다이얼러에 번호를 채워 주고 발신은 사용자가 누른다. */
    fun dial(context: Context, tel: String): Boolean =
        context.launch(Intent(Intent.ACTION_DIAL, "tel:${tel.trim()}".toUri()))

    /**
     * 카카오맵 길찾기. 앱이 깔려 있으면 앱이 받고, 없으면 웹 지도가 연다.
     * 좌표가 없는 장소가 있으므로(전체의 0.1%) 호출 전에 [Place.hasCoordinates] 를 본다.
     */
    fun route(context: Context, place: Place): Boolean {
        val lat = place.lat ?: return false
        val lng = place.lng ?: return false
        val name = Uri.encode(place.name)
        return context.launch(Intent(Intent.ACTION_VIEW, "$KAKAO_ROUTE/$name,$lat,$lng".toUri()))
    }

    fun share(context: Context, place: Place, shared: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shared)
            putExtra(Intent.EXTRA_SUBJECT, place.name)
        }
        return context.launch(Intent.createChooser(intent, null))
    }

    /** 받아 줄 앱이 없을 수 있다(에뮬레이터에는 다이얼러가 없기도 하다). 앱을 죽이지 않는다. */
    private fun Context.launch(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun String.toUri(): Uri = Uri.parse(this)

    private const val KAKAO_ROUTE = "https://map.kakao.com/link/to"
}

/** `places.source` 를 사람이 읽는 출처 이름으로. 표기는 의무다 (spec.md §8 · 개발계획서 §8.1). */
@StringRes
fun sourceLabelRes(source: String): Int = when (source) {
    "localdata" -> R.string.source_localdata
    "mfds" -> R.string.source_mfds
    "tourapi" -> R.string.source_tourapi
    "stddata" -> R.string.source_stddata
    "kcisa" -> R.string.source_kcisa
    else -> R.string.source_unknown
}
