package io.github.junkie300.petapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing

/**
 * 아직 안 만든 탭이 쓰는 화면.
 *
 * 빈 화면을 내놓지 않는다. **무엇이 없고 무엇을 기다리는지** 적는다 — 로딩·없음·실패를
 * 구분하라는 spec.md §5.3 과 같은 정신이다. 사용자가 "고장났나"로 읽으면 안 된다.
 * 일러스트는 넣지 않는다 (spec.md §6.4 — 빈 상태는 텍스트 + 액션).
 *
 * ⚠️ **여기서 끝나는 화면과 여기서 빠져나가는 화면은 다르다** (D-89). 아직 안 만든 탭(입양)은
 * 기다리는 것 말고 할 일이 없지만, 지도를 못 여는 기기(D-71)에서는 **그 동네 장소를 볼 다른
 * 길이 있다.** 있으면 [action] 으로 준다 — 없는 척하면 그 기기에서는 장소를 볼 방법이 사라진다.
 *
 * @param action 누를 것이 있을 때의 (문구, 하는 일). null 이면 안내만 남는다.
 */
@Composable
fun ComingSoonScreen(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // 바로 아래 제목이 같은 내용을 읽어 준다
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            val (label, onClick) = action
            Button(onClick = onClick, shape = PillShape) { Text(label) }
        }
    }
}
