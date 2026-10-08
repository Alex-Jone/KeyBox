package com.keybox.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.keybox.app.ui.AppViewModel
import com.keybox.app.ui.KeyBoxApp
import com.keybox.app.ui.theme.KeyBoxTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 关键安全措施：最近任务/截图保护
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            KeyBoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 监听生命周期：App 进入后台时立即锁定
                    DisposableEffect(Unit) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    // 进入后台（切走/锁屏）→ 立即锁定，防止后台泄露
                                    // 但若因打开文件选择器等外部 Activity 进入后台，则不锁
                                    if (appViewModel.shouldLockOnBackground()) {
                                        appViewModel.lock()
                                    }
                                }
                                else -> {}
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose { lifecycle.removeObserver(observer) }
                    }
                    KeyBoxApp(appViewModel)
                }
            }
        }
    }
}
