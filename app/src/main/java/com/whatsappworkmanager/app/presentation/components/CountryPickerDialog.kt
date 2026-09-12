package com.whatsappworkmanager.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.utils.CountryCode
import com.whatsappworkmanager.app.utils.CountryCodes

/**
 * A full, searchable country picker — a proper dedicated screen-like dialog rather than a
 * cramped dropdown, since this is meant for a deliberate, considered choice (the country
 * chosen here becomes the app-wide default everywhere a phone number is entered — see
 * [com.whatsappworkmanager.app.utils.CountryCodePrefs]), not a quick correction mid-form.
 *
 * [selectedDialCode] highlights the current selection (a checkmark) if it's visible in the
 * (possibly filtered) list — purely visual, doesn't affect filtering.
 */
@Composable
fun CountryPickerDialog(
    selectedDialCode: String?,
    onDismiss: () -> Unit,
    onSelect: (CountryCode) -> Unit
) {
    // Reads whatever locale actually got applied (LocaleHelper already resolved "system" to a
    // concrete language before this screen ever renders), rather than re-reading and
    // re-resolving the raw saved setting here too.
    val currentLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        CountryCodes.ALL.filter { CountryCodes.matches(it, query) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 12.dp)) {
                    Text(stringResource(R.string.country_picker_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.country_picker_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.country_picker_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { it.dialCode + it.name }) { country ->
                            val isSelected = country.dialCode == selectedDialCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(country) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(country.flag, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(country.displayName(currentLanguage), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    // U+200E (Left-to-Right Mark): anchors this run as LTR
                                    // explicitly — without it, a "+20" sitting inside an RTL
                                    // paragraph (the whole screen, in Arabic) can visually
                                    // reorder to "20+", since a bare "+" is BIDI-neutral and
                                    // takes its direction from context rather than carrying
                                    // one of its own.
                                    "\u200E+${country.dialCode}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
