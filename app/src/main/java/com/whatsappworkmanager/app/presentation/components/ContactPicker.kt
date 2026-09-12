package com.whatsappworkmanager.app.presentation.components

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.whatsappworkmanager.app.R

/**
 * A "Pick from Contacts" button using Android's system contact picker
 * (`Intent.ACTION_PICK` on the Phone content URI) — **no `READ_CONTACTS` permission is
 * requested or declared anywhere in this app**. Launching the picker hands the UI off to the
 * Contacts app itself; when the user picks someone, Android grants this app a temporary,
 * read-only URI permission scoped to *only that one contact's phone-number row* — not the
 * contacts database in general. That's enough to read back a name + number here, but nowhere
 * near enough to browse or bulk-read the address book, which is the deliberate boundary: this
 * app can act on a contact you explicitly hand it, one at a time, never enumerate them all.
 *
 * [onPicked] receives the contact's display name and raw phone number string exactly as
 * stored (formatting varies — may or may not include a leading "+" and country code; callers
 * that need country-code + local-number split should parse it themselves, e.g. via
 * `CountryCodes.split` when the raw string starts with "+").
 */
@Composable
fun PickFromContactsButton(
    label: String = stringResource(R.string.pick_from_contacts_default_label),
    onPicked: (name: String, phoneNumber: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (!name.isNullOrBlank()) onPicked(name, number)
            }
        }
    }

    Button(
        onClick = {
            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            launcher.launch(pickIntent)
        },
        modifier = modifier
    ) { Text(label) }
}
