package com.masterclock.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.masterclock.app.R

/**
 * [input] launched, or said that nothing on the device can take it. A file picker, the camera:
 * each is another app, and a device can have none for it -- a phone with its camera app disabled,
 * a stripped-down build with no document picker. The launch then throws, and the app closed. The
 * rules screen already said so for a missing PDF viewer; these did not.
 */
fun <I> ActivityResultLauncher<I>.launchOrSay(context: Context, input: I) {
    try {
        launch(input)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context.applicationContext, R.string.toast_no_app_for_this, Toast.LENGTH_LONG).show()
    }
}
