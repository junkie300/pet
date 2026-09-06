package io.github.junkie300.petapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.SheetShape
import io.github.junkie300.petapp.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 지도 위에 얹는 바텀시트의 3단 (spec.md §5.2 S-02 — peek 20% / half 50% / full 90%).
 *
 * 국내 지도앱의 관용 패턴이라 사용자가 배울 것이 없다. 세 단인 이유는 각 단이 하는 일이 달라서다:
 * peek 은 **지도가 주인공**, half 는 **지도와 목록을 함께**, full 은 **목록이 주인공**.
 * 두 단으로 줄이면 가운데가 사라져 지도를 보며 목록을 훑을 수가 없다.
 */
enum class SheetDetent(val visibleFraction: Float) {
    PEEK(0.20f),
    HALF(0.50f),
    FULL(0.90f),
    ;

    /** 손잡이를 누를 때 다음 단. 끝까지 올라갔으면 다시 내려온다. */
    fun next(): SheetDetent = entries[(ordinal + 1) % entries.size]
}

/**
 * 시트 상태. **상세로 갔다 돌아와도 그 자리에 있어야 한다** — 목록을 훑다가 하나 열어 본
 * 사람에게 시트가 접혀 있으면 훑던 자리를 처음부터 다시 찾아야 한다.
 *
 * 상태 객체 자체는 저장할 수 없으므로(위치·애니메이션이 들어 있다) **어느 단이었는지만**
 * 저장하고 돌아올 때 그 단에서 다시 만든다.
 */
@Composable
fun rememberPlaceSheetState(): AnchoredDraggableState<SheetDetent> {
    var saved by rememberSaveable { mutableStateOf(SheetDetent.PEEK.name) }
    val state = remember { AnchoredDraggableState(SheetDetent.valueOf(saved)) }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { detent -> saved = detent.name }
    }
    return state
}

/**
 * 장소 목록을 담는 바텀시트.
 *
 * 목록 자체는 [content] 로 받는다 — 목록 화면과 **같은 항목 코드**(`placeItems`)를 쓰기 위해서다 (D-26).
 *
 * ⚠️ 시트 안의 목록은 스크롤되고 시트 자체는 끌린다. 둘이 같은 방향 제스처를 다투므로
 * [sheetNestedScroll] 로 **목록이 끝까지 간 다음에** 시트가 움직이도록 넘겨준다.
 */
@Composable
fun PlaceSheet(
    state: AnchoredDraggableState<SheetDetent>,
    /** 시트가 놓이는 자리의 높이(px). 세 단의 위치를 여기서 계산한다. */
    containerHeightPx: Float,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: @Composable ColumnScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val anchors = remember(containerHeightPx) {
        DraggableAnchors {
            SheetDetent.entries.forEach { detent ->
                // 앵커는 시트 **위쪽 모서리의 y** 다. 20% 만 보이면 화면 높이의 80% 지점에 있다.
                detent at containerHeightPx * (1f - detent.visibleFraction)
            }
        }
    }
    // ⚠️ 앵커는 **그리기 전에** 정해져 있어야 한다. LaunchedEffect 로 미루면 첫 프레임의
    // offset 이 NaN 이라 시트가 화면 맨 위에 한 번 번쩍이고 내려온다.
    remember(anchors) { state.updateAnchors(anchors); anchors }

    val scope = rememberCoroutineScope()
    val nestedScroll = remember(state) { sheetNestedScroll(state) }
    val context = LocalContext.current

    Surface(
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, state.offsetOrDefault(anchors)) }
            .anchoredDraggable(state, Orientation.Vertical),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 손잡이는 끌 수도 있고 누를 수도 있다. 끄는 것을 모르는 사람이 반드시 있다.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clickable { scope.launch { state.animateTo(state.settledValue.next()) } }
                    .semantics { contentDescription = context.getString(R.string.cd_sheet_handle) },
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, PillShape),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                content = header,
            )

            // 머리말은 고정이고 목록은 그 아래로 지나간다. 선이 없으면 카드가 글자 중간에서
            // 잘린 것처럼 보인다 — 고장이 아니라 경계라는 것을 이 한 줄이 말해 준다.
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 10.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScroll),
                contentPadding = PaddingValues(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = 12.dp,
                    // 마지막 카드가 하단 탭에 가리지 않게 넉넉히 둔다.
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                content = content,
            )
        }
    }
}

/**
 * 시트 위치를 px 로 준다. 앵커가 붙기 전(첫 프레임)에는 [SheetDetent.PEEK] 자리에 둔다 —
 * `requireOffset()` 은 그 순간 예외를 던진다.
 */
private fun AnchoredDraggableState<SheetDetent>.offsetOrDefault(
    anchors: DraggableAnchors<SheetDetent>,
): Int {
    val current = offset
    val value = if (current.isNaN()) anchors.positionOf(SheetDetent.PEEK) else current
    return value.roundToInt()
}

/**
 * 목록이 스크롤을 다 쓰고 남긴 만큼으로 시트를 움직인다.
 *
 * - 위로 끌 때(음수)는 **시트를 먼저** 올린다. 시트가 반쯤 열린 채로 목록만 스크롤되면
 *   사용자는 시트가 고장 난 줄 안다.
 * - 아래로 끌 때는 **목록이 맨 위에 닿은 뒤에야** 시트가 내려간다 (onPostScroll).
 */
private fun sheetNestedScroll(state: AnchoredDraggableState<SheetDetent>): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            return if (delta < 0 && source == NestedScrollSource.UserInput) {
                Offset(0f, state.dispatchRawDelta(delta))
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = if (source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(available.y))
        } else {
            Offset.Zero
        }

        // 손을 뗐을 때. 시트가 두 단 사이에 떠 있으면 가까운 단으로 붙인다.
        // ⚠️ `settle(velocity)` 는 폐기됐다 — 목표 단은 이미 targetValue 가 알고 있으므로
        // 그 값으로 애니메이션한다.
        override suspend fun onPreFling(available: Velocity): Velocity {
            val offset = state.offset
            return if (available.y < 0 && !offset.isNaN() && offset > state.anchors.minPosition()) {
                state.animateTo(state.targetValue)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            state.animateTo(state.targetValue)
            return available
        }
    }
