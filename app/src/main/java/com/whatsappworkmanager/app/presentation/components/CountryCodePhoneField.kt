package com.whatsappworkmanager.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.utils.CountryCodes

/**
 * Country-code + phone-number pair, as one reusable field: a compact, *editable* country-code
 * box (shows the flag, accepts typing the code directly if the user already knows it, e.g.
 * "20") next to the local-number field. Tapping the dropdown arrow (or typing in the code box)
 * opens a list of countries filtered by whatever's typed so far — by dial code or by name — for
 * when the user doesn't know their country's code.
 *
 * Deliberately built on the plain, long-stable `DropdownMenu`/`DropdownMenuItem` rather than
 * Material3's `ExposedDropdownMenuBox`/`ExposedDropdownMenu` combo — that pair's API has shifted
 * across Compose Material3 versions (`menuAnchor()` signature changes, `ExposedDropdownMenu`
 * itself not resolving in some versions) and isn't worth the fragility for what's fundamentally
 * a simple "text field with a dropdown of suggestions" control.
 *
 * [countryCode]/[onCountryCodeChange] and [localNumber]/[onLocalNumberChange] are hoisted so
 * the caller decides what to do with them (e.g. persist the last-used code — see
 * [com.whatsappworkmanager.app.utils.CountryCodePrefs] — and combine them into one full digits
 * string for storage).
 */
@Composable
fun CountryCodePhoneField(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    localNumber: String,
    onLocalNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val flag = CountryCodes.byDialCode(countryCode)?.flag ?: "🏳️"
    val filteredCountries = remember(countryCode) {
        if (countryCode.isBlank()) {
            CountryCodes.ALL
        } else {
            CountryCodes.ALL.filter { CountryCodes.matches(it, countryCode) }
        }
    }

    Column(modifier = modifier) {
        // Explicitly forced LTR regardless of the app's current language — a phone number
        // (country code then local number) is conventionally written left-to-right even in an
        // otherwise fully right-to-left Arabic UI, the same way it would be in a real Arabic
        // phone app. Without this override, Compose's normal RTL-mirroring for Arabic would
        // flip this Row's visual order (local number field ends up on the left, country code
        // on the right) — not what a phone-number field should ever do.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.width(126.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = {
                            onCountryCodeChange(it.filter { ch -> ch.isDigit() })
                            expanded = true
                        },
                        label = { Text(stringResource(R.string.country_code_label)) },
                        placeholder = { Text("20") },
                        prefix = { Text("$flag +") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose country")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expanded && filteredCountries.isNotEmpty(),
                        onDismissRequest = { expanded = false }
                    ) {
                        filteredCountries.take(30).forEach { country ->
                            DropdownMenuItem(
                                text = { Text("${country.flag}  +${country.dialCode}  ${country.displayName(currentLanguage)}") },
                                onClick = {
                                    onCountryCodeChange(country.dialCode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = localNumber,
                    onValueChange = { onLocalNumberChange(it.filter { ch -> ch.isDigit() }) },
                    label = { Text(stringResource(R.string.phone_number_label)) },
                    placeholder = { Text("1001234567") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.country_code_phone_field_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
