package com.twintitanium.gifttracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twintitanium.gifttracker.ui.GiftTrackerViewModel
import com.twintitanium.gifttracker.ui.screens.GiftTrackerRoot
import com.twintitanium.gifttracker.ui.theme.GiftTrackerTheme
import com.twintitanium.gifttracker.widget.GiftTrackerWidget

class MainActivity : ComponentActivity() {

    private val viewModel: GiftTrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleLaunchIntent(intent)
        }

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            GiftTrackerTheme(
                themeSeed = prefs.themeSeed,
                colorMode = prefs.colorMode
            ) {
                GiftTrackerRoot(
                    viewModel = viewModel,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        when {
            intent.action == Intent.ACTION_VIEW &&
                intent.data?.scheme.equals("gifttracker", ignoreCase = true) &&
                intent.data?.host.equals("pair", ignoreCase = true) -> {
                intent.data?.let { viewModel.handleIncomingPairingUri(it) }
            }
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (!sharedText.isNullOrBlank() || !sharedSubject.isNullOrBlank()) {
                    viewModel.handleIncomingShare(sharedText, sharedSubject)
                }
            }
            intent.action == GiftTrackerWidget.ACTION_WIDGET_NEW_GIFT ||
                intent.getBooleanExtra(GiftTrackerWidget.EXTRA_OPEN_COMPOSER, false) -> {
                viewModel.openQuickAddFromWidget()
            }
        }
    }
}
