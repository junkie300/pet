package io.github.junkie300.petapp.ui.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 위치 권한을 묻는 자리가 둘이다 — 목록의 `거리 보기`(D-81)와 지도의 `현재 위치로`(D-82).
 * 둘이 **같은 것을 물어야** 한 곳에서 허용한 사람이 다른 곳에서 또 묻는 일이 없다.
 *
 * ⚠️ **대략 위치(COARSE)만 받아도 성공이다.** 우리가 위치로 하는 일은 카드에 `320m` 를 적고
 * 지도를 그 자리로 옮기는 것뿐이라, 정확한 위치를 못 받았다고 아무것도 못 하는 것처럼 굴면
 * 사용자가 일부러 고른 `대략`이 고장으로 읽힌다.
 *
 * 돌려주는 람다를 부르면 창이 뜬다. **이미 허용돼 있으면 창 없이 바로 결과가 온다.**
 */
@Composable
fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    // 창이 뜬 사이에 화면이 다시 그려져도, 결과는 **지금**의 람다가 받아야 한다.
    val current by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> current(granted.values.any { it }) }
    return remember(launcher) { { launcher.launch(LOCATION_PERMISSIONS) } }
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)
