package com.box.app.qs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.box.app.MainActivity

class TileLongPressActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        runCatching { startActivity(intent) }
        finish()
    }
}
