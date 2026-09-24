package dev.kosherswitch

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/*
 * QR-code setup: on a freshly reset phone, tap the welcome screen 6 times, scan the Kosher Switch
 * QR code, and Android downloads the app and makes it the device owner. No computer needed.
 * Android 12 asks the app these two questions during that setup.
 */

/** "How should this phone be managed?" — as a fully managed (kosher) phone. */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK, Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        ))
        finish()
    }
}

/** "Are your policies in place?" — yes; the rest happens in the app's own setup. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
