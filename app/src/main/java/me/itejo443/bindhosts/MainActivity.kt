package me.itejo443.bindhosts

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.widget.Toast
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check Notification Permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Notification permission granted, call the service to show the notification
            triggerNotificationService()
        }

        // Check Root (su) availability
        val rootStatusText: TextView = findViewById(R.id.root_status)
        if (isRootAvailable()) {
            rootStatusText.text = "Root Access: Enabled"
        } else {
            rootStatusText.text = "Root Access: Disabled"
        }
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val notificationStatusText: TextView = findViewById(R.id.notification_status)
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notificationStatusText.text = "Notification Permission: Granted"
                triggerNotificationService()
            } else {
                notificationStatusText.text = "Notification Permission: Denied"
            }
        }
    }

    private fun triggerNotificationService() {
        Toast.makeText(this, "Please add BindHosts Tile in Quickpanel", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "Start using Tile and Notification Post", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "To launch Notification Post, Click BindHosts Tile", Toast.LENGTH_LONG).show()
    }

    private fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c whoami")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            line != null && line.trim().equals("root", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
