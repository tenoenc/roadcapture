package com.tenacy.roadcapture.manager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class AppReviewManager @Inject constructor(
    @ActivityContext private val context: Context,
) {
    fun requestReview(onReviewCompleted: () -> Unit = {}) {
        try {
            requestInAppReview(onReviewCompleted)
        } catch (e: Exception) {
            Log.e("ReviewHelper", "In-app review failed", e)
            openPlayStore()
        }
    }

    private fun requestInAppReview(onReviewCompleted: () -> Unit) {
        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()

        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val activity = context as AppCompatActivity
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d("ReviewHelper", "In-app review completed")
                    onReviewCompleted()
                }
            } else {
                Log.e("ReviewHelper", "Failed to get review info", task.exception)
                openPlayStore()
            }
        }
    }

    private fun openPlayStore() {
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                setPackage("com.android.vending")
            }
            context.startActivity(playStoreIntent)
        } catch (e: ActivityNotFoundException) {
            // Play Store 앱이 없으면 웹 브라우저로
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
            }
            context.startActivity(webIntent)
        }
    }
}