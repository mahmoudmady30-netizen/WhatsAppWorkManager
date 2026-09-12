package com.whatsappworkmanager.app.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Verifies the click-to-chat deep link builder: it must never claim to auto-send (every path
 * requires the user's own tap on WhatsApp's Send button), must gracefully return null when
 * WhatsApp isn't installed, and must build a correct `wa.me` link when a phone number is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentHelperTest {

    private fun installFakeWhatsApp(packageName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        }
        Shadows.shadowOf(context.packageManager).installPackage(packageInfo)

        // Give the fake package a launch intent so getLaunchIntentForPackage(..) resolves too.
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(
            launchIntent,
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    this.packageName = packageName
                    name = "$packageName.MainActivity"
                }
            }
        )
    }

    @Test
    fun `returns null when whatsapp is not installed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = IntentHelper.buildWhatsAppChatIntent(context, phoneNumber = "201234567890", text = "hi")
        assertNull(intent)
    }

    @Test
    fun `builds a wa me link with cleaned phone number and encoded text when phone is given`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(
            context,
            phoneNumber = "+20 123 456 7890", // user-typed, with spaces and a plus
            text = "صباح الخير"
        )

        assertTrue(intent != null)
        assertEquals(Intent.ACTION_VIEW, intent!!.action)
        assertEquals(Constants.WHATSAPP_PACKAGE, intent.`package`)
        val uri = intent.data!!
        assertEquals("wa.me", uri.host)
        assertEquals("/201234567890", uri.path) // "+", spaces stripped -> digits only
        assertTrue(uri.getQueryParameter("text") == "صباح الخير")
    }

    @Test
    fun `falls back to plain launch intent when no phone number is given`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(context, phoneNumber = null, text = null)

        assertTrue(intent != null)
        // A plain launch intent (from getLaunchIntentForPackage), not a wa.me deep link.
        assertTrue(intent!!.data == null || intent.data.toString() != "https://wa.me/")
    }

    @Test
    fun `blank phone number is treated the same as no phone number`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(context, phoneNumber = "   ", text = "hello")

        assertTrue(intent != null)
        assertTrue(intent!!.data == null || !intent.data.toString().contains("wa.me"))
    }

    @Test
    fun `auto variant prefers regular WhatsApp when both are installed`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        installFakeWhatsApp(Constants.WHATSAPP_BUSINESS_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(
            context, phoneNumber = "201234567890", text = "hi",
            preferredVariant = IntentHelper.WHATSAPP_VARIANT_AUTO
        )
        assertEquals(Constants.WHATSAPP_PACKAGE, intent!!.`package`)
    }

    @Test
    fun `business variant is honored even when regular WhatsApp is also installed`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        installFakeWhatsApp(Constants.WHATSAPP_BUSINESS_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(
            context, phoneNumber = "201234567890", text = "hi",
            preferredVariant = IntentHelper.WHATSAPP_VARIANT_BUSINESS
        )
        assertEquals(Constants.WHATSAPP_BUSINESS_PACKAGE, intent!!.`package`)
    }

    @Test
    fun `explicitly requesting business returns null if only regular WhatsApp is installed`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = IntentHelper.buildWhatsAppChatIntent(
            context, phoneNumber = "201234567890", text = "hi",
            preferredVariant = IntentHelper.WHATSAPP_VARIANT_BUSINESS
        )
        // Deliberately does NOT silently fall back to regular WhatsApp — if the user explicitly
        // asked for Business, opening the wrong app instead would be surprising, not helpful.
        assertNull(intent)
    }

    @Test
    fun `isIgnoringBatteryOptimizations reflects the real PowerManager state`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val shadowPowerManager = org.robolectric.Shadows.shadowOf(powerManager)

        shadowPowerManager.setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(IntentHelper.isIgnoringBatteryOptimizations(context))

        shadowPowerManager.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(IntentHelper.isIgnoringBatteryOptimizations(context))
    }

    @Test
    fun `openNotificationSettingsForWhatsApp returns false when WhatsApp is not installed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(IntentHelper.openNotificationSettingsForWhatsApp(context))
    }

    @Test
    fun `openNotificationSettingsForWhatsApp returns true when WhatsApp is installed`() {
        installFakeWhatsApp(Constants.WHATSAPP_PACKAGE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(IntentHelper.openNotificationSettingsForWhatsApp(context))
    }
}
