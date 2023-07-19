package game.paulgross.tictactoe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        enableMessagesFromGameServer()

        Log.d(TAG, "Getting GameServer ...")
        GameServer.activate(applicationContext, getPreferences(MODE_PRIVATE))
        GameServer.queueClientRequest("netStatus:")
    }

    override fun onBackPressed() {
        //  Prevents the back button from working.
    }

    fun onClickBack(view: View) {
        val intent: Intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    fun onClickJoinRemote(view: View) {
        // TODO
    }

    private fun enableMessagesFromGameServer() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX)
        registerReceiver(gameMessageReceiver, intentFilter)
        Log.d(TAG, "Enabled message receiver for [${packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX}]")
    }

    private fun disableMessagesFromGameServer() {
        unregisterReceiver(gameMessageReceiver)
    }

    /**
    Receive messages from the GameServer.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast message = [$intent]")

            val ipAddress = intent.getStringExtra("ipaddress")

            if (ipAddress != null) {
                Log.d(TAG, "Received IP Address = [$ipAddress]")
                findViewById<TextView>(R.id.textViewIPAddress).text = ipAddress
            }
        }
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
        val DISPLAY_MESSAGE_SUFFIX = ".$TAG.display.UPDATE"
    }
}


