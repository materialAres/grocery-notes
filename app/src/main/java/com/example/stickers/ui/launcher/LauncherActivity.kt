package com.example.stickers.ui.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stickers.R
import com.example.stickers.appContainer
import com.example.stickers.ui.theme.StickersTheme

/** The app's own (minimal) screen: how-to, "Add note to home screen", Recently deleted. */
class LauncherActivity : ComponentActivity() {

    private val viewModel: BinViewModel by viewModels {
        viewModelFactory {
            initializer { BinViewModel(appContainer.repository, appContainer.widgetPinner) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StickersTheme {
                LauncherApp(viewModel)
            }
        }
    }
}

@Composable
private fun LauncherApp(viewModel: BinViewModel) {
    var showBin by rememberSaveable { mutableStateOf(false) }

    if (showBin) {
        BackHandler { showBin = false }
        BinScreen(viewModel, onBack = { showBin = false })
    } else {
        HomeScreen(viewModel, onOpenBin = { showBin = true })
    }

    if (viewModel.showPinUnsupported) {
        val appName = stringResource(R.string.app_name)
        AlertDialog(
            onDismissRequest = viewModel::dismissPinUnsupported,
            title = { Text(stringResource(R.string.pin_unsupported_title)) },
            text = { Text(stringResource(R.string.pin_unsupported_body, appName)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPinUnsupported) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(viewModel: BinViewModel, onOpenBin: () -> Unit) {
    val binCount by viewModel.binCount.collectAsStateWithLifecycle()
    val appName = stringResource(R.string.app_name)

    Scaffold(topBar = { TopAppBar(title = { Text(appName) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_howto_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_howto_body, appName), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(onClick = viewModel::addNoteToHomeScreen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_add_note))
            }

            ListItem(
                modifier = Modifier.clickable(onClick = onOpenBin),
                headlineContent = { Text(stringResource(R.string.home_recently_deleted)) },
                trailingContent = {
                    if (binCount > 0) Badge { Text(binCount.toString()) }
                },
            )
        }
    }
}
