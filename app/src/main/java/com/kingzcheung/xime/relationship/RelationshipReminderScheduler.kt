package com.kingzcheung.xime.relationship

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.R
import com.kingzcheung.xime.relationship.db.CommitmentEntity
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RelationshipReminderScheduler {
    private const val ACTION_REMIND = "com.kingzcheung.xime.RELATIONSHIP_REMINDER"
    private const val EXTRA_COMMITMENT_ID = "commitment_id"

    fun schedule(context: Context, commitment: CommitmentEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(commitment.dueAt, System.currentTimeMillis() + 1_000),
            pendingIntent(context, commitment.id),
        )
    }

    fun cancel(context: Context, commitmentId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, commitmentId))
    }

    private fun pendingIntent(context: Context, commitmentId: String): PendingIntent {
        val intent = Intent(context, RelationshipReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_COMMITMENT_ID, commitmentId)
        return PendingIntent.getBroadcast(
            context,
            commitmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class RelationshipReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "relationship_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "关系提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Misaki 关系提醒")
            .setContentText("你有一条待处理的承诺或关系事项")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                intent.getStringExtra("commitment_id")?.hashCode() ?: 1,
                notification,
            )
        }
    }
}

class RelationshipBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RelationshipDatabase.getInstance(context)
                    .relationshipDao()
                    .getCommitments()
                    .filter { it.status == CommitmentStatus.PENDING.name }
                    .forEach { RelationshipReminderScheduler.schedule(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
