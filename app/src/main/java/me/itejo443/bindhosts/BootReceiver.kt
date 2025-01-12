package me.itejo443.bindhosts


import android.app.PendingIntent
import android.app.NotificationManager
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.view.View
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // Log that we received the intent
        Log.d(TAG, "Received broadcast: ${intent.action}")

        // Check if it's a boot completion event
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed action received")

            // Retrieve saved state from SharedPreferences
            val sharedPreferences = context.getSharedPreferences("tile_prefs", Context.MODE_PRIVATE)
            val savedState = sharedPreferences.getInt("tile_state", Tile.STATE_INACTIVE)
            val savedTime = sharedPreferences.getString("tile_time", "") ?: ""
            val lastToastMessage = sharedPreferences.getString("last_toast_message", "") ?: ""

            Log.d(TAG, "Restoring state - State: $savedState, Time: $savedTime")

            // Restore the tile state by starting the RunScriptTileService
            val tileServiceIntent = Intent(context, RunScriptTileService::class.java)
            tileServiceIntent.putExtra("tile_state", savedState)
            context.startService(tileServiceIntent)
            Log.d(TAG, "Started RunScriptTileService with state: $savedState")

            // Prepare the notification text based on the saved state
            val statusText = if (savedState == Tile.STATE_ACTIVE) {
                "active: $savedTime\n$lastToastMessage"
            } else {
                "reset: $savedTime\n$lastToastMessage"
            }

            Log.d(TAG, "Notification status text: $statusText")

            // Create an Intent to handle notification click
            val notificationIntent = Intent(context, NotificationClickReceiver::class.java).apply {
                action = "com.itejo443.BINDHOSTS_NOTIFICATION_CLICKED"
            }

            // PendingIntent to execute on notification click
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Determine if the status bar is light or dark (for correct icon choice)
            val isLightStatusBar = isLightStatusBar(context)
            val notificationIcon = if (isLightStatusBar) {
                R.drawable.ic_launcher_dark // Dark icon for light status bar
            } else {
                R.drawable.ic_launcher_light // Light icon for dark status bar
            }

            // Log the icon choice based on the status bar appearance
            Log.d(TAG, "Using notification icon: ${if (isLightStatusBar) "Dark" else "Light"}")

            // Build and display the notification
            val notification = NotificationCompat.Builder(context, "bindhosts_status_channel")
                .setContentTitle("BindHosts Status")
                .setContentText(statusText)
                .setSmallIcon(notificationIcon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Keeps the notification active while service is running
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, notification)
            Log.d(TAG, "Notification displayed with status: $statusText")

            // Optionally show a Toast for debugging purposes
            if (savedState == Tile.STATE_ACTIVE) {
                Toast.makeText(context, "Tile Restored to Active", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Toast: Tile Restored to Active")
            } else {
                Toast.makeText(context, "Tile Restored to Inactive", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Toast: Tile Restored to Inactive")
            }
        }
    }

    // Helper function to check if the status bar is light
    @Suppress("DEPRECATION")
    private fun isLightStatusBar(context: Context): Boolean {
        val activity = context as? Activity ?: return false

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val window = activity.window
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                val isLight = windowInsetsController.isAppearanceLightStatusBars
                Log.d(TAG, "isLightStatusBar (R+): $isLight")
                isLight // Safe access for Android R+
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val decorView = activity.window.decorView
                val isLight = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
                Log.d(TAG, "isLightStatusBar (M+): $isLight")
                isLight
            }
            else -> {
                Log.d(TAG, "isLightStatusBar (below M): false")
                false
            }
        }
    }
}
