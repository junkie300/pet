package io.github.junkie300.petapp.ui.place

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.GeoPoint

/**
 * 거리 표기를 켜는 줄 (`spec.md §6.4`, D-81).
 *
 * **먼저 묻지 않는다.** 화면을 열자마자 위치 권한 창을 띄우면, 이 앱이 왜 위치를 원하는지
 * 모르는 채로 거절하게 된다 — 그러면 거리는 영영 안 뜬다. 사용자가 "거리 보기"를 누른
 * 다음에 묻는다.
 *
 * 위치를 이미 얻었으면 아무 것도 그리지 않는다. 그때는 **카드가 거리로 말한다.**
 */
@Composable
fun DistanceRow(
    origin: GeoPoint?,
    onLocationGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (origin != null) return

    // 거절당한 뒤에도 버튼은 남긴다. 두 번째 누름은 시스템이 창을 안 띄울 수 있으므로,
    // **왜 안 뜨는지**를 그 자리에 적어 준다 — 버튼만 멀쩡히 있으면 고장으로 읽힌다.
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            denied = false
            onLocationGranted()
        } else {
            denied = true
        }
    }
    val permissions = remember {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { launcher.launch(permissions) }) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.distance_enable),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (denied) {
            Text(
                text = stringResource(R.string.distance_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
