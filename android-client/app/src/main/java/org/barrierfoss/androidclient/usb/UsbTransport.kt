package org.barrierfoss.androidclient.usb

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object UsbTransport {
    const val USB_HOST = "127.0.0.1"

    fun isUsbConnected(context: Context): Boolean {
        if (isUsbStateConnected(context)) {
            return true
        }
        return isUsbPowerConnected(context)
    }

    private fun isUsbStateConnected(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
        val connected = intent?.extras?.getBoolean(EXTRA_CONNECTED) == true
        val configured = intent?.extras?.getBoolean(EXTRA_CONFIGURED) == true
        return connected || configured
    }

    private fun isUsbPowerConnected(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0
    }

    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_CONFIGURED = "configured"
}
