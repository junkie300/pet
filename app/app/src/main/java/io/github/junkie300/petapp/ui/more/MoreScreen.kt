package io.github.junkie300.petapp.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.BuildConfig
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.Spacing

/**
 * 더보기 탭 (spec.md §5.1).
 *
 * 시안의 "마이" 탭을 이름만 바꿔 받은 자리다 — 로그인 없는 앱이라 계정 화면이 들어갈 곳이
 * 없다 (D-39). 즐겨찾기(S-07)·데이터 출처(S-08)·앱 정보만 담는다.
 *
 * **데이터 출처 표기는 지금부터 넣는다.** 공공데이터 이용 표기는 법적 의무이고
 * (개발계획서 §8.1 · spec.md §8), 나중에 붙이는 것으로 미룰 성질이 아니다.
 */
@Composable
fun MoreScreen(onFavoritesClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.tab_more),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        SectionCard(onClick = onFavoritesClick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.more_favorites),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        SectionCard {
            Text(
                text = stringResource(R.string.more_sources),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            // 지금 실려 있는 소스만 적는다. 카테고리가 늘 때마다 한 줄씩 붙인다.
            Text(
                text = stringResource(R.string.more_source_region),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.more_source_hospital),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // 장소별 기준일은 S-03 상세에서 그 장소의 것으로 보여준다 (D-40).
                text = stringResource(R.string.more_source_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text(
                text = stringResource(R.string.more_app_info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // SIL OFL 1.1 은 저작권 표시를 함께 배포할 것을 요구한다. 전문은 앱 안에 넣어 두었다
            // (assets/licenses/pretendard_ofl.txt).
            Text(
                text = stringResource(R.string.more_font_license),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
