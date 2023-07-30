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

    private var periodicUpdateThread: PeriodicUpdater? = null

    class PeriodicUpdater: Thread() {

        private val refreshPeriodMilliseconds = 500L

        private var working = true
        override fun run() {
            while (working) {
                GameServer.queueActivityMessage("UpdateSettings")
                sleep(refreshPeriodMilliseconds)
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

        // Maybe use push messages from the GameServer instead?
        // onCreate starts push, onPause stops push???
        periodicUpdateThread = PeriodicUpdater()
        periodicUpdateThread!!.start()
    }

    override fun onPause() {
        super.onPause()
        periodicUpdateThread?.shutdown()
    }

    override fun onBackPressed() {
        backToGameplay()
    }

    fun onClickBack(view: View) {
        backToGameplay()
    }

    private fun backToGameplay() {
        val intent: Intent = Intent(this, GameplayActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    fun onClickLocal(view: View) {
        if (GameServer.getGameMode() == GameServer.GameMode.SERVER) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.title_stop_server))
            builder.setMessage(getString(R.string.confirm_stop_server))
            builder.setPositiveButton(getString(R.string.button_confirm)) { _, _ ->
                GameServer.queueActivityMessage("StartLocal:")
            }
            builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
            builder.show()
            return
        }

        if (GameServer.getGameMode() == GameServer.GameMode.CLIENT) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.title_stop_client))
            builder.setMessage(getString(R.string.confirm_stop_client))
            builder.setPositiveButton(getString(R.string.button_confirm)) { _, _ ->
                GameServer.queueActivityMessage("StartLocal:")
            }
            builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
            builder.show()
            return
        }
    }

    fun onClickStartServer(view: View) {
        if (GameServer.getGameMode() == GameServer.GameMode.LOCAL) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.button_start_server))
            builder.setMessage(getString(R.string.confirm_start_server))
            builder.setPositiveButton(getString(R.string.button_confirm)) { _, _ ->
                GameServer.queueActivityMessage("StartServer:")
            }
            builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
            builder.show()
        }
    }

    fun onClickJoinRemote(view: View) {
        if (GameServer.getGameMode() == GameServer.GameMode.LOCAL) {
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
                GameServer.queueActivityMessage("RemoteServer:$remoteIP")
            }
            builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
            builder.show()
        }
    }

    fun onClickPrevAddress(view: View) {
        if (allIpAddresses?.size!! < 2) {
            return
        }
        val currAddress = findViewById<TextView>(R.id.textViewIPAddress).text

        // determine current index
        val index = allIpAddresses?.indexOf(currAddress)
        if (index != null) {
            if (index < 1) {
                return
            }
            findViewById<TextView>(R.id.textViewIPAddress).text = allIpAddresses!![index - 1]
        }
    }

    fun onClickNextAddress(view: View) {
        if (allIpAddresses?.size!! < 2) {
            return
        }
        val currAddress = findViewById<TextView>(R.id.textViewIPAddress).text

        // determine current index
        val index = allIpAddresses?.indexOf(currAddress)
        if (index != null) {
            if (index > allIpAddresses?.size!! - 2) {
                return
            }
            findViewById<TextView>(R.id.textViewIPAddress).text = allIpAddresses!![index + 1]
        }
    }

    private fun enableMessagesFromGameServer() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX)
        registerReceiver(gameMessageReceiver, intentFilter)
        Log.d(TAG, "Enabled message receiver for [${packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX}]")
    }

    private var allIpAddresses: MutableList<String>? = mutableListOf()
//    private var remoteServerName = ""
//    private var countOfClients = 0

    /**
    Receive messages from the GameServer.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ipAddressList = intent.getStringExtra("IpAddressList")
            val currMode = intent.getStringExtra("CurrMode")
//            val currStatus = intent.getStringExtra("CurrStatus")  // FIXME - delete this

            // FIXME: - doesn't work yet
            val remoteServer = intent.getStringExtra("RemoteServer")
            val clientCount = intent.getStringExtra("ClientCount")


            // FIXME: - doesn't work yet
            if (remoteServer != null) {
                val status = String.format(getString(R.string.remote_server), remoteServer)
                findViewById<TextView>(R.id.textViewStatus).text = status
            }

            // FIXME: - doesn't work yet
            if (clientCount != null) {
                val clients = String.format(getString(R.string.message_connected_clients), clientCount)
                findViewById<TextView>(R.id.textViewStatus).text = clients
            }

            if (currMode != null) {
                var modeText = ""
                if (currMode == GameServer.GameMode.SERVER.toString()) {
                    modeText = getString(R.string.mode_server)
                }
                if (currMode == GameServer.GameMode.CLIENT.toString()) {
                    modeText = getString(R.string.mode_client)
                    allIpAddresses?.clear()
                    findViewById<TextView>(R.id.textViewIPAddress).text = getString(R.string.message_server_not_running)
                }
                if (currMode == GameServer.GameMode.LOCAL.toString()) {
                    modeText = getString(R.string.mode_local)
                    allIpAddresses?.clear()
                    findViewById<TextView>(R.id.textViewIPAddress).text = getString(R.string.message_server_not_running)
                }
                findViewById<TextView>(R.id.textViewCurrentMode).text = modeText
            }

            if (ipAddressList != null) {
                if (ipAddressList == "") {
                    findViewById<TextView>(R.id.textViewIPAddress).text = getString(R.string.message_server_not_running)
                } else {
                    val newIpAddresses = ipAddressList.split(",")
                    if (newIpAddresses != allIpAddresses) {
                        allIpAddresses?.clear()
                        allIpAddresses?.addAll(newIpAddresses)
                        val primaryAddress = allIpAddresses?.get(allIpAddresses!!.lastIndex)
                        findViewById<TextView>(R.id.textViewIPAddress).text = primaryAddress
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
        val DISPLAY_MESSAGE_SUFFIX = ".$TAG.display.UPDATE"
    }
}


