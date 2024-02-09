package com.zhelenskiy.vault

import App
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.startup.Initializer
import repositories.appContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

class ContextInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        // WorkManager.getInstance() is non-null only after
        // WorkManager is initialized.
        appContext = context
        return context
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf()
    }
}
