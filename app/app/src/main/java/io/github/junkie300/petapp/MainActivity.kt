package io.github.junkie300.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.region.RegionPickerScreen
import io.github.junkie300.petapp.ui.region.RegionPickerViewModel
import io.github.junkie300.petapp.ui.theme.PetAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetApplication).container

        setContent {
            PetAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    if (!container.isConfigured) {
                        // 키가 없어도 앱이 죽지 않는다. 왜 비었는지 화면으로 알려준다.
                        Text(
                            text = stringResource(R.string.state_not_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(insets).padding(16.dp),
                        )
                    } else {
                        PetAppRoot(container = container, modifier = Modifier.padding(insets))
                    }
                }
            }
        }
    }
}

/**
 * 지금은 S-01 한 화면뿐이다. 지도(S-02)·상세(S-03)가 붙는 시점에
 * Navigation Compose 의 NavHost 로 바꾼다.
 */
@Composable
private fun PetAppRoot(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: RegionPickerViewModel = viewModel(
        factory = RegionPickerViewModel.factory(
            container.regionRepository,
            container.recentRegionStore,
        ),
    )
    Column(modifier = modifier) {
        RegionPickerScreen(
            viewModel = viewModel,
            // TODO(1단계): 지도 화면(S-02)으로 이동한다. 지금은 선택만 저장하고 머문다.
            onRegionConfirmed = { _: Region -> },
        )
    }
}
