package me.hexf.nzcpc.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.hexf.nzcpc.R
import me.hexf.nzcpc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val CHANNEL_ID = "me.hexf.nzcpc.notifications.scanlauncher"
    private val notificationId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    fun openPreferences(view: View) {
        val intent = Intent(view.context, SettingsActivity::class.java)
        startActivity(intent)

    }

    fun openScan(view: View){
        val intent = Intent(view.context, ScanActivity::class.java)
        startActivity(intent)
    }

    fun createNotification(view: View){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(view.context, ScanActivity::class.java).apply{
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(view.context, 0, intent, 0)

        val builder = NotificationCompat.Builder(view.context,  CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_icon)
            .setContentText("Tap here to verify a CovidPass")
            .setContentText("Launch CovidPass Checker")
            .setPriority(NotificationCompat.PRIORITY_MAX) //push to top
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(view.context)){
            notify(notificationId, builder.build())
        }
    }

    fun clearNotification(view: View){
        with(NotificationManagerCompat.from(this)){
            cancel(notificationId)
        }
    }
}