package io.github.junkie300.petapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.ui.nav.PetApp
import io.github.junkie300.petapp.ui.theme.PetAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetApplication).container

        setContent {
            PetAppTheme {
                if (!container.isConfigured) {
                    // 키가 없어도 앱이 죽지 않는다. 왜 비었는지 화면으로 알려준다.
                    // 탭바까지 그리면 눌러도 전부 빈 화면이라 안내가 묻힌다 — 이때만 단일 화면이다.
                    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                        Text(
                            text = stringResource(R.string.state_not_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(insets).padding(16.dp),
                        )
                    }
                } else {
                    // 탭·화면 구성은 전부 PetApp 안에 있다 (ui/nav/PetApp.kt).
                    PetApp(container = container)
                }
            }
        }
    }
}
