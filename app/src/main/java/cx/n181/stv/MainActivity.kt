package cx.n181.stv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cx.n181.stv.ui.AppNavHost
import cx.n181.stv.ui.PrivateTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrivateTvTheme {
                AppNavHost()
            }
        }
    }
}
