package com.mp.macro_plugin_compose.ui


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dark.plugins.api.PluginApi
import com.dark.plugins.api.PluginInfo
import com.dark.plugins.sys.uiAction.CommandBridgeService
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import org.json.JSONObject

class UiActionPlugin(private val context: Context) : PluginApi(context) {

    private var messenger: Messenger? = null
    private var pendingJson: String? = null


    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            messenger = Messenger(binder)
            Log.d("UiActionPlugin", "Bound to CommandBridgeService")

            pendingJson?.let { jsonString ->
                val msg = Message.obtain()
                val bundle = Bundle()
                bundle.putString("command", jsonString)
                msg.data = bundle
                messenger?.send(msg)
                Log.d("UiActionPlugin", "Sent command: $jsonString")
                pendingJson = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            messenger = null
        }
    }

    override fun onCreate(data: Any) {
        super.onCreate(data)
        Log.d("UiActionPlugin", "onCreate")

        val intent = Intent(context, CommandBridgeService::class.java)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)

        // Send JSON once service is connected
        if (data is JSONObject) {
            pendingJson = data.toString()
        }
    }


    override fun onDestroy() {
        context.unbindService(conn)
        super.onDestroy()
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo("UiActionPlugin", "Handles UI tool calls via accessibility bridge")
    }

    @Composable
    override fun AppContent(){
        NeuroVersePluginTheme {
            Button(onClick = {

            }) {
                Text("Send Click Command")
            }
        }
    }
}