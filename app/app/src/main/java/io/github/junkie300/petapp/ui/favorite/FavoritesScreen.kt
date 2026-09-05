package io.github.junkie300.petapp.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import io.github.junkie300.petapp.ui.common.DetailScaffold
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import io.github.junkie300.petapp.ui.place.PlaceCard
import io.github.junkie300.petapp.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * S-07 즐겨찾기 (spec.md §5.2).
 *
 * **로컬 DB 만 본다 — 네트워크를 타지 않는다.** 그래서 실패 상태가 없다.
 * 비어 있는 것은 실패가 아니라 "아직 담은 게 없다"이며, 그 둘을 가르라는 §5.3 의 요구가
 * 여기서는 자동으로 지켜진다.
 */
class FavoritesViewModel(favorites: FavoriteDao) : ViewModel() {

    val state: StateFlow<UiState<List<FavoritePlace>>> = favorites.observeAll()
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), UiState.Loading)

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        fun factory(favorites: FavoriteDao) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FavoritesViewModel(favorites) as T
        }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onPlaceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(R.string.more_favorites),
        onBack = onBack,
        modifier = modifier,
    ) { insets ->
        val bodyModifier = Modifier
            .padding(insets)
            .padding(horizontal = Spacing.screenHorizontal)

        when (val current = state) {
            is UiState.Loading -> SkeletonRows(count = 3, modifier = bodyModifier)
            is UiState.Empty -> EmptyMessage(stringResource(R.string.favorites_empty), bodyModifier)
            // 로컬 DB 라 실패가 날 자리가 없다. 타입을 맞추기 위한 가지다.
            is UiState.Failed -> EmptyMessage(stringResource(R.string.favorites_empty), bodyModifier)
            is UiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.place_list_count, current.data.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(current.data, key = { it.placeId }) { favorite ->
                    PlaceCard(
                        name = favorite.name,
                        address = favorite.address,
                        category = PlaceCategory.fromDbValue(favorite.category),
                        tel = favorite.tel,
                        onClick = { onPlaceClick(favorite.placeId) },
                    )
                }
            }
        }
    }
}
