package com.tenacy.roadcapture.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val subscriptionManager: SubscriptionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val isActive = subscriptionManager.checkSubscriptionStatus()

            // 작업 완료 시간 기록
            SubscriptionPref.lastSubscriptionCheckTime = System.currentTimeMillis()

            Log.d("SubscriptionCheck", "구독 상태: $isActive")

            // 취소되었고 만료가 임박한 경우 알림 표시
            if (SubscriptionPref.isCancelledButStillValid() &&
                SubscriptionPref.daysUntilExpiry() in 1 .. Constants.SUBSCRIPTION_EXPIRY_WARNING_DAYS) {
                showSubscriptionExpiringNotification()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SubscriptionCheck", "구독 확인 실패", e)
            Result.retry()
        }
    }

    private fun showSubscriptionExpiringNotification() {
        val notificationManager = NotificationManagerCompat.from(context)

        // 알림 채널 생성
        val channel = NotificationChannel(
            Constants.SUBSCRIPTION_NOTIFICATION_CHANNEL_ID,
            Constants.SUBSCRIPTION_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        // 만료까지 남은 일수
        val daysLeft = SubscriptionPref.daysUntilExpiry()

        // 알림 생성
        val builder = NotificationCompat.Builder(context, Constants.SUBSCRIPTION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("구독 만료 예정")
            .setContentText("구독이 ${daysLeft}일 후에 만료됩니다. 계속 이용하시려면 갱신하세요.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // 알림 클릭 시 앱 열기
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        // 알림 표시
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationManager.notify(Constants.SUBSCRIPTION_NOTIFICATION_ID_EXPIRING, builder.build())
    }

    companion object {
        private const val TAG = "SubscriptionCheckWorker"

        fun enqueueOneTimeWork(context: Context, duration: Long) {
            val delay = duration + Constants.SUBSCRIPTION_EXPIRY_CHECK_DELAY_MS

            val expiryWork = OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
                .addTag(TAG)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.SUBSCRIPTION_WORK_NAME_EXPIRY_CHECK,
                ExistingWorkPolicy.REPLACE,
                expiryWork
            )
        }

        fun enqueuePeriodicWork(context: Context) {
            // 정기 체크
            val periodicWork = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
                Constants.SUBSCRIPTION_REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .addTag(TAG)
                .setInitialDelay(Constants.SUBSCRIPTION_INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.SUBSCRIPTION_WORK_NAME_PERIODIC_CHECK,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWork
            )
        }

        fun cancelAll(context: Context) {
            Log.d(TAG, "구독 상태 확인 워커 취소")
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}