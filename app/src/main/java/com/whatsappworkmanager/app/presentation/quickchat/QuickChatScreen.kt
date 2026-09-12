package com.whatsappworkmanager.app.presentation.quickchat

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.presentation.components.CountryCodePhoneField
import com.whatsappworkmanager.app.presentation.components.PickFromContactsButton
import com.whatsappworkmanager.app.utils.CountryCodePrefs
import com.whatsappworkmanager.app.utils.CountryCodes
import com.whatsappworkmanager.app.utils.IntentHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WwmApplication
    val scope = rememberCoroutineScope()

    var countryCode by remember { mutableStateOf(CountryCodePrefs.getLastUsed(context)) }
    var localNumber by remember { mutableStateOf("") }
    val defaultVariant by app.settingsDataStore.whatsappVariant.collectAsStateWithLifecycle(
        initialValue = IntentHelper.WHATSAPP_VARIANT_REGULAR
    )
    var selectedVariant by remember { mutableStateOf<String?>(null) }
    val effectiveVariant = selectedVariant ?: when (defaultVariant) {
        IntentHelper.WHATSAPP_VARIANT_BUSINESS -> IntentHelper.WHATSAPP_VARIANT_BUSINESS
        else -> IntentHelper.WHATSAPP_VARIANT_REGULAR
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(R.string.quick_chat_title), style = MaterialTheme.typography.titleLarge)
                        Text("WA premium", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Chat, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Open a WhatsApp chat", style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.quick_chat_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(R.string.quick_chat_number_label), style = MaterialTheme.typography.titleSmall)
                    }
                    CountryCodePhoneField(
                        countryCode = countryCode,
                        onCountryCodeChange = { countryCode = it },
                        localNumber = localNumber,
                        onLocalNumberChange = { localNumber = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    PickFromContactsButton(
                        onPicked = { _, pickedNumber ->
                            if (!pickedNumber.isNullOrBlank()) {
                                val fallback = CountryCodes.byDialCode(countryCode) ?: CountryCodes.ALL.first()
                                val (country, local) = CountryCodes.parsePickedContactNumber(pickedNumber, fallback)
                                countryCode = country.dialCode
                                localNumber = local
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(stringResource(R.string.quick_chat_which_whatsapp), style = MaterialTheme.typography.titleSmall)
                            Text("Choose the app for this chat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            IntentHelper.WHATSAPP_VARIANT_REGULAR to stringResource(R.string.onboarding_whatsapp_variant_regular),
                            IntentHelper.WHATSAPP_VARIANT_BUSINESS to stringResource(R.string.quick_chat_business_label)
                        ).forEach { (value, label) ->
                            val selected = effectiveVariant == value
                            if (selected) Button(
                                onClick = { selectedVariant = value },
                                modifier = Modifier.weight(1f).height(50.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            } else OutlinedButton(
                                onClick = { selectedVariant = value },
                                modifier = Modifier.weight(1f).height(50.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (localNumber.isNotBlank()) {
                        CountryCodePrefs.setLastUsed(context, countryCode)
                        scope.launch {
                            com.whatsappworkmanager.app.utils.IntentHelper.openWhatsAppChat(
                                context,
                                countryCode + localNumber,
                                "",
                                effectiveVariant
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Filled.Chat, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text(stringResource(R.string.quick_chat_open_button), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
