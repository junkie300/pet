package io.github.junkie300.petapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 현재 위치 한 곳 (`spec.md §6.4` 의 카드 거리, D-59·D-81).
 *
 * **Play 서비스(`FusedLocationProviderClient`)를 쓰지 않는다.** 우리가 위치로 하는 일은
 * "카드에 320m 라고 적는 것" 하나이고, 그 정도는 안드로이드 기본 [LocationManager] 로 충분하다.
 * 의존성을 하나 덜 지면 APK 도, 막혔을 때 읽을 문서도 줄어든다 (spec.md §2 의 선택 기준).
 *
 * ⚠️ **위치는 없을 수 있다.** 권한을 안 줬거나, 껐거나, 실내라 못 잡는다. 그래서 전부 null 을
 * 돌려주고 **화면은 거리를 빼고 그대로 그린다** — 거리는 있으면 좋은 것이지 이 앱의 기준이
 * 아니다. 이 앱의 기준점은 사용자가 고른 지역이다 (D-24·D-59).
 */
class DeviceLocation(private val context: Context) {

    /** 대략이든 정확하든, 하나라도 있으면 거리를 적을 수 있다. */
    val hasPermission: Boolean
        get() = PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * 지금 위치. 못 얻으면 null 이다.
     *
     * 마지막으로 알려진 위치를 **먼저** 본다. 몇 분 전 값이라도 거리 표기에는 충분하고,
     * 새로 잡으려 들면 실내에서 몇 초씩 기다리게 된다 — 캐시 우선과 같은 생각이다 (D-79).
     */
    suspend fun current(): GeoPoint? = withContext(Dispatchers.IO) {
        if (!hasPermission) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        lastKnown(manager)?.let { return@withContext it.toPoint() }
        withTimeoutOrNull(FRESH_TIMEOUT_MS) { fresh(manager) }?.toPoint()
    }

    /** 켜져 있는 제공자들의 마지막 위치 중 **가장 최근 것**. 오래되면 버린다. */
    private fun lastKnown(manager: LocationManager): Location? = providers(manager)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < MAX_AGE_MS }
        .maxByOrNull { it.time }

    /**
     * 한 번만 받아 본다.
     *
     * ⚠️ **제공자 하나만 물어보고 포기하지 않는다.** 목록의 첫 제공자가 켜져 있어도 답을 못 줄 수
     * 있다(실내의 GPS, 대리자가 없는 기기의 FUSED). 그때 그냥 기다리면 아무것도 없는 쪽을 보며
     * 시간을 다 쓴다 — 하나씩 짧게 물어보고 넘어간다.
     */
    private suspend fun fresh(manager: LocationManager): Location? {
        for (provider in providers(manager)) {
            val location = withTimeoutOrNull(PER_PROVIDER_MS) { await(manager, provider) }
            if (location != null) return location
        }
        return null
    }

    private suspend fun await(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = androidx.core.os.CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    /**
     * 쓸 만한 제공자를 좋은 순서로.
     *
     * ⚠️ **꺼진 제공자는 거른다.** 기기가 목록에는 갖고 있어도 사용자가 GPS 를 꺼 두면
     * 영영 답이 오지 않는다 — 타임아웃으로 때우면 그만큼 화면이 늦어진다.
     */
    private fun providers(manager: LocationManager): List<String> = PROVIDERS
        .filter { it in manager.allProviders }
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        /**
         * `FUSED_PROVIDER` 는 API 31 부터다. 없으면 통신망 → GPS 순이다 —
         * 거리 한 줄에 GPS 를 먼저 깨울 이유가 없다.
         */
        val PROVIDERS = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )

        /** 이보다 오래된 마지막 위치는 안 쓴다. 지난주 위치로 거리를 적으면 거짓말이 된다. */
        const val MAX_AGE_MS = 10 * 60 * 1000L

        /** 새로 잡는 데 이만큼 넘게 걸리면 포기한다. 거리는 화면을 붙잡을 만한 값이 아니다. */
        const val FRESH_TIMEOUT_MS = 8_000L

        /** 제공자 하나에 주는 시간. 이 안에 답이 없으면 다음 제공자로 넘어간다. */
        const val PER_PROVIDER_MS = 3_000L
    }
}
