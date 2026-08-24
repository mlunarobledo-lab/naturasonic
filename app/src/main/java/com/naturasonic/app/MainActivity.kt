package com.naturasonic.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.naturasonic.app.security.SecurityManager
import com.naturasonic.app.ui.navigation.NaturaSonicNavHost
import com.naturasonic.app.ui.screens.lock.LockScreen
import com.naturasonic.app.ui.theme.NaturaSonicTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                securityManager.onAppResumed()
            }
        }

        setContent {
            NaturaSonicTheme {
                val isLocked by securityManager.isLocked.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLocked) {
                        LockScreen(onUnlocked = { securityManager.unlock() })
                    } else {
                        NaturaSonicNavHost()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        securityManager.onAppBackgrounded()
    }
}
