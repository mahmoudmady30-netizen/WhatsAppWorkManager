package com.whatsappworkmanager.app.presentation.privacy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.presentation.components.ScreenPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.privacy_title)) }) }
    ) { padding ->
        Text(
            stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(padding)
                .padding(ScreenPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        )
    }
}
