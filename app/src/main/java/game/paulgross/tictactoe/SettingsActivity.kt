package game.paulgross.tictactoe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class SettingsActivity : AppCompatActivity() {

    val periodicUpdateThread: PeriodicUpdater = PeriodicUpdater()

    class PeriodicUpdater: Thread() {

        private var working = true
        override fun run() {
            while (working) {
                GameServer.queueClientRequest("UpdateSettings")
                sleep(1000L)
            }
        }

        fun shutdown() {
            working = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        enableMessagesFromGameServer()

        Log.d(TAG, "Getting GameServer ...")
        GameServer.activate(applicationContext, getPreferences(MODE_PRIVATE))

        periodicUpdateThread.start()
    }

    override fun onPause() {
        super.onPause()
        periodicUpdateThread.shutdown()
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

    fun onClickLocal(view: View) {
        GameServer.queueClientRequest("StartLocal:")
    }

    fun onClickStartServer(view: View) {
        GameServer.queueClientRequest("StartServer:")
    }

    fun onClickJoinRemote(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remote Connect")
        builder.setMessage("Enter Remote Address")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        val preferences = getPreferences(MODE_PRIVATE)
        val prevServer = preferences.getString("RemoteServer", "192.168.1.").toString()
        input.setText(prevServer)
        builder.setView(input)

        builder.setPositiveButton("Join") { _, _ ->
            val remoteIP = input.text.toString()
            val editor = preferences.edit()
            editor.putString("RemoteServer", remoteIP)
            editor.apply()
            Log.d(TAG, "TODO Connect to $remoteIP")
            GameServer.queueClientRequest("RemoteServer:$remoteIP")
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
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

    private var allIpAddresses: List<String>? = null

    /**
    Receive messages from the GameServer.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast message = [$intent]")

            val ipAddressList = intent.getStringExtra("IpAddressList")

            if (ipAddressList != null) {
                Log.d(TAG, "Received IP Address = [$ipAddressList]")
                // TODO - delete this simple textview
                findViewById<TextView>(R.id.textViewIPAddress).text = ipAddressList

                allIpAddresses = ipAddressList.split(",")
                val primaryAddress = allIpAddresses?.get(allIpAddresses!!.lastIndex)
                findViewById<TextView>(R.id.textViewAllIPpAddresses).text = primaryAddress
            }
        }
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
        val DISPLAY_MESSAGE_SUFFIX = ".$TAG.display.UPDATE"
    }
}


