package game.paulgross.tictactoe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


class MainActivity : AppCompatActivity() {

    private var localGameServer: GameServer? = null

    private var displaySquareList: MutableList<TextView?> = mutableListOf()

    private var colorOfWinning: Int? = null

    private var textPlayerView: TextView? = null

    override fun onPause() {
        disableMessagesFromGameServer()
        super.onPause()

        // TODO: Because screen rotation calls onPause()...
        //  Maybe track "paused" flag so clients temporarily pause comms???
        //  What happens if the user makes a move during the rotate pause?
        //  Clients need to be sent the PAUSED state
        //  In PAUSED state no new client GUI actions can be started until PAUSED clears.
        //  In PAUSED state Server stops processing the client queue.
        //  After leaving PAUSED state, server resumes queue processing.
        //  NOTE should put a timestamp in the client queue,so that old client actions are removed.
        //  But if the onPause is quickly resumed, the client will likely never notice.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main)
        } else {
            setContentView(R.layout.activity_main_landscape)
        }
        /*if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_landscape)
        } else {
            //The default layout is portrait
            setContentView(R.layout.activity_main)
        }*/
/*        viewModelFactory = ActivityViewModelFactory()
        viewModel = ViewModelProvider(this, viewModelFactory).get(ActivityViewModel::class.java)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_landscape)
        } else {
            //The default layout is portrait
            setContentView(R.layout.activity_main)
        }*/

        // Add all the display squares into the list.
        // NOTE: The squares MUST BE added in index order.
        displaySquareList.add(findViewById(R.id.textSquare1))
        displaySquareList.add(findViewById(R.id.textSquare2))
        displaySquareList.add(findViewById(R.id.textSquare3))
        displaySquareList.add(findViewById(R.id.textSquare4))
        displaySquareList.add(findViewById(R.id.textSquare5))
        displaySquareList.add(findViewById(R.id.textSquare6))
        displaySquareList.add(findViewById(R.id.textSquare7))
        displaySquareList.add(findViewById(R.id.textSquare8))
        displaySquareList.add(findViewById(R.id.textSquare9))

        colorOfReset = getColor(R.color.green_pastel)
        colorOfWinning = getColor(R.color.orange_pastel)

        textPlayerView = findViewById(R.id.textPlayer)

        enableMessagesFromGameServer()
        attachToLocalGameServer()
    }

    override fun onStop() {
        super.onStop()
        // TODO - ask the game server to pause until activity awakes again...
    }

/*    private fun reAttachToGameServer() {
        viewModelFactory = ActivityViewModelFactory()
        viewModel = ViewModelProvider(this, viewModelFactory).get(ActivityViewModel::class.java)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_landscape)
        } else {
            //The default layout is portrait
            setContentView(R.layout.activity_main)
        }
    }*/

    private fun attachToLocalGameServer() {
        // First check to see if the link to the local GameServer is already stored in the ViewModel
//        var viewModel: ActivityViewModel
        var viewModelFactory: ActivityViewModelFactory = ActivityViewModelFactory()
        var viewModel = ViewModelProvider(this, viewModelFactory).get(ActivityViewModel::class.java)
        localGameServer = viewModel.getGameServer()

        if (localGameServer != null) {
            Log.d(TAG, "Reattached to the local GameServer.")
        } else {
            Log.d(TAG, "Starting a new local GameServer ...")
            localGameServer = GameServer(applicationContext, getPreferences(MODE_PRIVATE))
            localGameServer?.start()
            viewModel.setGameServer(localGameServer!!)  // Store the link in the local ViewModel
        }
    }

    private fun stopGameServer() {
        Log.d(TAG, "Stopping the game server ...")
        localGameServer?.shutdown()
    }

    /*
        User Interface functions start here.
     */

    /**
     * Updates the current grid state into the display squares.
     */
    private fun displayGrid(gridStateString: String?) {
        for (i in 0..8) {
            val state = GameServer.SquareState.valueOf(gridStateString?.get(i).toString())
            val view = displaySquareList[i]

            if (state == GameServer.SquareState.E) {
                view?.text = ""
            } else {
                view?.text = state.toString()
            }
        }
    }

    private fun resetGridDisplay() {
        displaySquareList.forEach{ square ->
            square?.setBackgroundColor(colorOfReset!!)
            square?.text = ""
        }
    }

    private fun toastWinner(winner: String) {
        Toast.makeText(
            this,
            String.format(getString(R.string.winner_message), winner),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun displayVictory(winSquares: String) {
        val s1 = Integer.parseInt(winSquares[0].toString())
        val s2 = Integer.parseInt(winSquares[1].toString())
        val s3 = Integer.parseInt(winSquares[2].toString())

        displaySquareList[s1]?.setBackgroundColor(colorOfWinning!!)
        displaySquareList[s2]?.setBackgroundColor(colorOfWinning!!)
        displaySquareList[s3]?.setBackgroundColor(colorOfWinning!!)
    }

    /**
     *  Handler for when the User clicks a square in the playing grid.
     */
    fun onClickPlaySquare(view: View) {
        val gridIndex = displaySquareList.indexOf(view as TextView)
        if (gridIndex == -1) {
            Log.d(TAG, "The user did NOT click a grid square.")
            return
        }
        localGameServer?.queueClientRequest("s:$gridIndex")
    }

    private fun displayCurrPlayer(player: String) {
        (textPlayerView as TextView).text =
            String.format(getString(R.string.curr_player_message), player)
    }

    fun onClickNewGame(view: View) {
        // Ask user to confirm new game
        // FIXME: The buttons on the AlertDialog have different colours to the layout.
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.new_game_title_message))
        builder.setMessage(getString(R.string.new_game_confirm_message))
        builder.setPositiveButton(getString(R.string.new_button_message)) { _, _ ->
            localGameServer?.queueClientRequest("reset:")
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
    }

    fun onClickExitApp(view: View) {
        // Ask user to confirm exit
        // FIXME: The buttons on the AlertDialog have different colours to the layout.
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.exit_title_message))
        builder.setMessage(getString(R.string.exit_confirm_message))
        builder.setPositiveButton(getString(R.string.exit_message)) { _, _ ->
            stopGameServer()
            finishAndRemoveTask()
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
    }

    /**
        Receive messages from the GameServer.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast message = [$intent]")

            val resetFlag = intent.getBooleanExtra("reset", false)
            val gridStateString = intent.getStringExtra("grid")
            val playerString = intent.getStringExtra("player")
            val winsquaresString = intent.getStringExtra("winsquares")
            val winnerString = intent.getStringExtra("winner")
            val ipAddress = intent.getStringExtra("ipaddress")

            if (resetFlag) {
                resetGridDisplay()
            }
            if (ipAddress != null) {
                Log.d(TAG, "Received IP Address = [$ipAddress]")
                findViewById<TextView>(R.id.textViewIPAddress).text = ipAddress
            }
            if (gridStateString != null) {
                Log.d(TAG, "Received current grid State = [$gridStateString]")
                displayGrid(gridStateString)
            }
            if (playerString != null) {
                Log.d(TAG, "Received current playerString = [$playerString]")
                displayCurrPlayer(playerString)
            }
            if (winsquaresString != null) {
                Log.d(TAG, "Received current winsquaresString = [$winsquaresString]")
                displayVictory(winsquaresString)
            }
            if (winnerString != null) {
                Log.d(TAG, "Received current winnerString = [$winnerString]")
                toastWinner(winnerString)
            }
        }
    }

    private fun enableMessagesFromGameServer() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(packageName + "display.UPDATE")
        registerReceiver(gameMessageReceiver, intentFilter)
    }

    private fun disableMessagesFromGameServer() {
        unregisterReceiver(gameMessageReceiver)
    }

    private var colorOfReset: Int? = null

    /**
        The ViewModel and its Factory ensures that we recover a link to the GameServer after screen rotation.
    */
    class ActivityViewModel(): ViewModel() {

        private var gameServer: GameServer? = null
        fun getGameServer(): GameServer? {
            return gameServer
        }

        fun setGameServer(theServer: GameServer) {
            gameServer = theServer
        }
    }

    class ActivityViewModelFactory(): ViewModelProvider.Factory {

        override  fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ActivityViewModel::class.java)){
                return ActivityViewModel() as T
            }
            throw IllegalArgumentException("Unknown View Model Class")
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

}