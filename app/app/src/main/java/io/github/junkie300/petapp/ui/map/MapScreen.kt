package io.github.junkie300.petapp.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.ComingSoonScreen
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.place.PlaceListUiState
import io.github.junkie300.petapp.ui.place.PlaceListViewModel
import io.github.junkie300.petapp.ui.place.placeItems
import io.github.junkie300.petapp.ui.theme.CategoryColor
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * S-02 장소 지도 — 고른 읍면동의 장소를 핀으로 찍는다 (spec.md §5.2).
 *
 * **목록과 같은 [PlaceListViewModel] 을 쓴다.** 지도와 목록은 같은 질문("이 동네의 이 카테고리")에
 * 대한 두 가지 그림일 뿐이라 조회를 두 벌 만들 이유가 없다 (D-26).
 *
 * 아직 첫 단계다 — 바텀시트 3단·카테고리 필터 칩·클러스터링은 다음 차례다. 지금은 **핀이 제자리에
 * 뜨는지 눈으로 확인하는 것**이 목적이다 (D-70).
 */
@Composable
fun MapScreen(
    viewModel: PlaceListViewModel,
    onPlaceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 지도만 안내로 바뀐다. 앱은 그대로 돈다 (spec.md §1.2).
    // 키가 없는 것과 SDK 를 못 올린 것은 **다른 말이다** — 고칠 사람이 볼 곳이 서로 다르다.
    if (!KakaoMapProvider.isConfigured) {
        ComingSoonScreen(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_no_key_title),
            body = stringResource(R.string.map_no_key_body),
            modifier = modifier,
        )
        return
    }
    KakaoMapProvider.startupError?.let { error ->
        ComingSoonScreen(
            icon = Icons.Outlined.Map,
            title = stringResource(R.string.map_unsupported_title),
            body = stringResource(R.string.map_unsupported_body),
            note = error,
            modifier = modifier,
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val categoryName = stringResource(viewModel.category.labelRes)
    val ready = state as? PlaceListUiState.Ready

    val places = ((ready?.places as? UiState.Success)?.data).orEmpty()
    val pins = remember(places, viewModel.category) { places.toPins(viewModel.category) }
    val region = ready?.region
    val regionCenter = remember(region) {
        val lat = region?.centerLat
        val lng = region?.centerLng
        if (lat != null && lng != null) LatLng.from(lat, lng) else null
    }

    // 지도가 검게 뜨는 사고는 예외도 로그도 분명하지 않다 (D-69). 받은 메시지를 화면에 그대로 적는다.
    var mapError by remember { mutableStateOf<String?>(null) }

    // 상단 알림이 지도를 가린다. 그만큼을 지도에 알려 줘야 핀이 알림 뒤로 들어가지 않는다 (D-73).
    var noticeHeightPx by remember { mutableIntStateOf(0) }

    // 지도에서 고른 핀. 시트에서 그 카드가 강조된다. **상세로 바로 가지 않는다** — 핀 하나를
    // 누른 것으로 화면을 통째로 바꾸면 지도를 훑어보던 맥락이 끊긴다 (D-74).
    var selectedPlaceId by rememberSaveable { mutableStateOf<Long?>(null) }

    val sheetState = rememberPlaceSheetState()
    val listState = rememberLazyListState()

    // 지역이 바뀌면 그전에 고른 핀은 이 목록에 없다. 그때만 지운다.
    // ⚠️ "지역 코드가 바뀌면 지운다"로 쓰면 안 된다 — 상세에 다녀오는 사이 지역이 잠시
    // null 이 되었다가 돌아오므로, 돌아올 때마다 선택이 사라진다(실측).
    LaunchedEffect(places) {
        if (places.isNotEmpty() && places.none { it.id == selectedPlaceId }) selectedPlaceId = null
    }

    LaunchedEffect(selectedPlaceId, places) {
        val index = places.indexOfFirst { it.id == selectedPlaceId }
        if (index < 0) return@LaunchedEffect
        // peek 은 목록이 거의 안 보인다. 핀을 눌렀으면 최소한 half 까지는 올려 준다.
        if (sheetState.settledValue == SheetDetent.PEEK) sheetState.animateTo(SheetDetent.HALF)
        listState.animateScrollToItem(index)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        // 시트가 아래를 덮는 만큼도 지도에 알려 준다. 안 그러면 아래쪽 핀이 시트 뒤에 숨는다.
        val sheetInsetPx = (containerHeightPx * SheetDetent.PEEK.visibleFraction).roundToInt()

        MapCanvas(
            center = regionCenter,
            pins = pins,
            topInsetPx = noticeHeightPx,
            bottomInsetPx = sheetInsetPx,
            onPinClick = { placeId -> selectedPlaceId = placeId },
            onMapError = { mapError = it.message ?: it.javaClass.simpleName },
            modifier = Modifier.fillMaxSize(),
        )

        // 상단에는 **지도에 관한 것**만 얹는다. 목록의 상태는 시트가 말한다.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp)
                .fillMaxWidth()
                .onSizeChanged { noticeHeightPx = it.height },
        ) {
            ready?.cachedAt?.let { OfflineBanner(cachedAt = it) }
            mapError?.let { error ->
                MapNotice(text = stringResource(R.string.map_error, error), emphasis = true)
            }
        }

        PlaceSheet(
            state = sheetState,
            containerHeightPx = containerHeightPx,
            listState = listState,
            header = {
                SheetHeader(
                    title = when {
                        state is PlaceListUiState.NoRegion -> stringResource(R.string.place_list_no_region)
                        region == null -> stringResource(R.string.map_loading)
                        // 좌표가 없는 장소는 핀이 될 수 없다. 조용히 빼면 목록의 건수와 어긋난다.
                        pins.size != places.size -> stringResource(
                            R.string.map_summary_missing_coords,
                            region.fullName,
                            categoryName,
                            pins.size,
                            places.size - pins.size,
                        )

                        else -> stringResource(
                            R.string.map_summary,
                            region.fullName,
                            categoryName,
                            places.size,
                        )
                    },
                )
            },
        ) {
            when {
                state is PlaceListUiState.NoRegion -> Unit

                state is PlaceListUiState.Failed ->
                    item { FailedMessage(onRetry = viewModel::retry) }

                ready == null || ready.places is UiState.Loading ->
                    item { SkeletonRows(count = 4) }

                ready.places is UiState.Failed ->
                    item { FailedMessage(onRetry = viewModel::retry) }

                ready.places is UiState.Empty ->
                    item { EmptyMessage(text = stringResource(R.string.place_list_empty, categoryName)) }

                else -> placeItems(
                    places = places,
                    onPlaceClick = { place -> onPlaceClick(place.id) },
                    selectedId = selectedPlaceId,
                )
            }
        }
    }
}

/** 시트 머리말 — 지금 무엇을 보고 있는지 한 줄. 시트가 peek 이어도 이건 보인다. */
@Composable
private fun SheetHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 지도 위에 얹는 알림 한 칸. 지도가 배경이므로 반드시 불투명한 판 위에 올린다. */
@Composable
private fun MapNotice(text: String, emphasis: Boolean = false) {
    MapNotice {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasis) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun MapNotice(content: @Composable () -> Unit) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
    }
}

/** 지도에 찍을 한 점. 좌표가 없는 장소는 여기까지 오지 못한다. */
data class MapPin(
    val placeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory,
)

private fun List<Place>.toPins(fallbackCategory: PlaceCategory): List<MapPin> = mapNotNull { place ->
    val lat = place.lat ?: return@mapNotNull null
    val lng = place.lng ?: return@mapNotNull null
    MapPin(place.id, place.name, lat, lng, place.placeCategory ?: fallbackCategory)
}

/**
 * 카카오맵 [MapView] 를 Compose 안에 얹는다.
 *
 * [MapView] 는 안드로이드 뷰이고 자기 수명주기를 따로 갖는다 — resume·pause·finish 를 짝 맞춰
 * 불러 주지 않으면 탭을 옮길 때마다 렌더러가 남는다.
 */
@Composable
private fun MapCanvas(
    center: LatLng?,
    pins: List<MapPin>,
    /** 상단 알림에 가려지는 높이(px). 카메라가 이만큼을 빼고 화면을 잡는다. */
    topInsetPx: Int,
    /** 바텀시트에 가려지는 높이(px). 같은 이유로 아래쪽도 빼 준다. */
    bottomInsetPx: Int,
    onPinClick: (Long) -> Unit,
    onMapError: (Exception) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val fitPaddingPx = remember(density) { with(density) { FIT_PADDING.roundToPx() } }

    // 콜백은 지도를 시작할 때 한 번만 등록된다. 그 안에서 최신 람다·값을 보게 해 둔다.
    val currentPinClick by rememberUpdatedState(onPinClick)
    val currentMapError by rememberUpdatedState(onMapError)
    val currentCenter by rememberUpdatedState(center)

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val mapView = remember {
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit

                    // 키가 틀리거나 콘솔에 패키지명·키 해시가 없으면 여기로 온다 (D-69).
                    override fun onMapError(error: Exception) = currentMapError(error)
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        map.setOnLabelClickListener { _, _, label ->
                            val placeId = label.tag as? Long
                            if (placeId == null) {
                                false
                            } else {
                                currentPinClick(placeId)
                                true
                            }
                        }
                        kakaoMap = map
                    }

                    // 지도가 준비되기 전의 첫 화면 위치. 고른 지역이 없으면 전국이 보이는 자리다.
                    override fun getPosition(): LatLng = currentCenter ?: DEFAULT_CENTER

                    override fun getZoomLevel(): Int =
                        if (currentCenter == null) NATIONWIDE_ZOOM else REGION_ZOOM
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    // 지도에게 "이만큼은 가려져 있다"고 알려 준다. 이걸 안 하면 fitMapPoints 가 잡아 준 위쪽
    // 핀이 상단 알림 뒤로 들어간다 — 화면으로 보기 전에는 드러나지 않는 종류의 어긋남이다.
    LaunchedEffect(kakaoMap, topInsetPx, bottomInsetPx) {
        kakaoMap?.setPadding(0, topInsetPx, 0, bottomInsetPx)
    }

    // 핀은 지도가 준비된 뒤에만 그릴 수 있다. 목록이 바뀌면 통째로 다시 그린다 — 한 읍면동의
    // 한 카테고리는 수십 개라, 지우고 다시 찍는 편이 차이를 계산하는 것보다 싸고 안전하다.
    LaunchedEffect(kakaoMap, pins, fitPaddingPx) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labels = map.labelManager ?: return@LaunchedEffect
        val layer = labels.layer ?: return@LaunchedEffect
        layer.removeAll()

        if (pins.isEmpty()) {
            center?.let { map.moveCamera(CameraUpdateFactory.newCenterPosition(it, REGION_ZOOM)) }
            return@LaunchedEffect
        }

        // 스타일은 카테고리마다 하나면 된다. 핀 개수만큼 만들면 같은 그림을 수십 벌 올리게 된다.
        val styles = pins.map { it.category }.distinct().associateWith { category ->
            labels.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(pinBitmap(context, category))
                        .setTextStyles(PIN_TEXT_SIZE, PIN_TEXT_COLOR, PIN_TEXT_STROKE, PIN_TEXT_STROKE_COLOR),
                ),
            )
        }

        layer.addLabels(
            pins.map { pin ->
                LabelOptions.from(LatLng.from(pin.latitude, pin.longitude))
                    .setStyles(styles[pin.category])
                    .setTexts(LabelTextBuilder().setTexts(pin.name))
                    .setClickable(true)
                    // 핀을 눌렀을 때 어느 장소인지 되찾는 유일한 끈이다.
                    .setTag(pin.placeId)
            },
        )

        // 한 점만 있으면 fitMapPoints 가 최대 배율까지 당겨 버린다. 그때는 그 점을 중심으로만 잡는다.
        val points = pins.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray()
        map.moveCamera(
            if (points.size == 1) {
                CameraUpdateFactory.newCenterPosition(points.first(), REGION_ZOOM)
            } else {
                // 핀 옆에 이름이 붙으므로 여백을 넉넉히 준다. 그래도 아주 긴 이름은 걸린다 —
                // 이름끼리 겹치는 것은 클러스터링(④)에서 함께 다룬다.
                CameraUpdateFactory.fitMapPoints(points, fitPaddingPx)
            },
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 핀 그림. 한 장을 카테고리 색으로 tint 해서 쓴다 — 색깔별 파일을 두지 않는다.
 *
 * ⚠️ 지도 바탕은 테마와 무관하게 밝다. 그래서 다크 짝(`CategoryColor.*Dark`)이 아니라
 * **라이트 기준 5색**을 쓴다. 어두운 카드 위에서 밝은 짝을 쓰는 것과 같은 이유다 (D-56).
 */
private fun pinBitmap(context: Context, category: PlaceCategory): Bitmap {
    val drawable = requireNotNull(context.getDrawable(R.drawable.ic_map_pin)).mutate()
    DrawableCompat.setTint(drawable, pinColor(category))
    return drawable.toBitmap()
}

private fun pinColor(category: PlaceCategory) = when (category) {
    PlaceCategory.HOSPITAL -> CategoryColor.Vet
    PlaceCategory.GROOMING -> CategoryColor.Grooming
    PlaceCategory.RESTAURANT -> CategoryColor.Restaurant
    PlaceCategory.TOUR -> CategoryColor.Tour
    PlaceCategory.WILDLIFE_CENTER -> CategoryColor.WildlifeCenter
}.toArgb()

/** 고른 지역이 없을 때의 첫 화면 — 남북으로 전국이 고르게 들어오는 자리다. */
private val DEFAULT_CENTER = LatLng.from(36.3, 127.8)
private const val NATIONWIDE_ZOOM = 7
private const val REGION_ZOOM = 14

/**
 * fitMapPoints 의 여백. 핀 옆에 이름이 붙으므로 그림 크기보다 넉넉히 준다.
 * ⚠️ 더 키우면 여백을 확보하려고 **축척이 통째로 물러난다** — 이름끼리 겹치는 것은
 * 여백이 아니라 클러스터링(④)으로 푼다.
 */
private val FIT_PADDING = 40.dp

private const val PIN_TEXT_SIZE = 26
private const val PIN_TEXT_COLOR = 0xFF1A1A1A.toInt()
private const val PIN_TEXT_STROKE = 3
private const val PIN_TEXT_STROKE_COLOR = 0xFFFFFFFF.toInt()
