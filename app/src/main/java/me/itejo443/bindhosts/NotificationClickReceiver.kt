package me.itejo443.bindhosts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class NotificationClickReceiver : BroadcastReceiver() {
    private var lastClickTime: Long = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        val currentTime = System.currentTimeMillis()
        
        // Only handle the click if it is at least 3 second after the last click
        if (currentTime - lastClickTime > 3000) {
            lastClickTime = currentTime

            context?.let { ctx ->
                // Check if the app is in the foreground
                val isAppInForeground = isAppInForeground(ctx)

                // Prepare the service intent
                val serviceIntent = Intent(ctx, RunScriptTileService::class.java)
                serviceIntent.action = "com.itejo443.BINDHOSTS_RUN_SCRIPT"

                // If the app is in the foreground or it's Android 8.0 and above, start as a foreground service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || isAppInForeground) {
                    Log.d("NotificationClickReceiver", "Starting service in foreground")
                    ContextCompat.startForegroundService(ctx, serviceIntent)
                } else {
                    Log.d("NotificationClickReceiver", "Starting service in background")
                    ctx.startService(serviceIntent)
                }
            }
        } else {
            Log.d("NotificationClickReceiver", "Ignoring click due to rate limiting")
        }
    }

    // Helper function to check if the app is in the foreground
    private fun isAppInForeground(context: Context): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses
            val packageName = context.packageName

            for (process in runningAppProcesses) {
                if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    process.processName == packageName) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationClickReceiver", "Error checking app foreground state", e)
        }
        return false
    }
}
