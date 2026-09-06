package io.github.junkie300.petapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.ui.theme.PillShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "지금 보는 것은 받아 둔 정보다" 한 줄 (spec.md §5.3 — 오프라인 배너).
 *
 * **언제 받은 것인지까지 적는다.** 이 앱은 상세마다 기준일을 적어서 신뢰를 얻는 도구인데
 * (D-40), 캐시로 그린 화면에 시각이 없으면 그 약속이 화면마다 어긋난다.
 *
 * ⚠️ **오류 빨강(`error`)을 쓰지 않는다.** 그 색은 응급·오류 전용이다 (spec.md §6.2).
 * 오프라인은 오류가 아니라 상태다. `secondaryContainer` 도 쓰지 않는다 — 테마가 그 칸을
 * 채워 두지 않아 M3 기본 보라가 나온다 (D-45). 팔레트에 실제로 있는 색만 쓴다.
 */
@Composable
fun OfflineBanner(cachedAt: Long, modifier: Modifier = Modifier) {
    val moment = remember(cachedAt) { formatCachedAt(cachedAt) }

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null, // 바로 옆 문구가 같은 내용을 읽어 준다
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.state_offline, moment),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "9월 6일 14:20". 앱이 한국어 전용이라 서식을 문자열 자원이 아니라 여기 둔다. */
private val CACHED_AT_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

private fun formatCachedAt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CACHED_AT_FORMAT)
