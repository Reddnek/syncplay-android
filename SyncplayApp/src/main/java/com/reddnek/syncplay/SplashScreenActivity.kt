package com.reddnek.syncplay

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.reddnek.syncplay.main.MainActivity
import com.reddnek.syncplayutils.SyncplayUtils

@SuppressLint("CustomSplashScreen") /* Don't trust Google's SplashScreen API, we make our own */
class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Enable IMMERSIVE MODE right off the bat */
        SyncplayUtils.hideSystemUI(this, false)

        /* Delaying transition */
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@SplashScreenActivity, MainActivity::class.java))
            finish()
        }, 500)


    }

}