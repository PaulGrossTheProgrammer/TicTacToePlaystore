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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class GameplayActivity : AppCompatActivity() {

    private var displaySquareList: MutableList<TextView?> = mutableListOf()
    private var textPlayerView: TextView? = null
    private var textStatusView: TextView? = null

    private var colorOfWin: Int? = null
    private var colorOfReset: Int? = null

    override fun onPause() {
        disableMessagesFromGameServer()
        super.onPause()

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
            setContentView(R.layout.activity_gameplay)
        } else {
            setContentView(R.layout.activity_gameplay_landscape)
        }

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
        colorOfWin = getColor(R.color.orange_pastel)

        textPlayerView = findViewById(R.id.textPlayer)
        textStatusView = findViewById(R.id.textViewPlayStatus)

        enableMessagesFromGameServer()

        GameServer.activate(applicationContext, getPreferences(MODE_PRIVATE))
        GameServer.queueActivityMessage("status:")
    }

    override fun onStop() {
        super.onStop()
        // TODO - ask the game server to pause until activity awakes again...
    }

    public override fun onBackPressed() {
        confirmExitApp()
    }

    private fun stopGameServer() {
        Log.d(TAG, "Stopping the game server ...")
        GameServer.queueActivityMessage("StopGame")
    }

    /**
     * Updates the current grid state into the display squares.
     */
    private fun displayGrid(grid: Array<GameServer.SquareState>) {
        for (i in 0..8) {
            val view = displaySquareList[i]
            val state = grid[i]

            if (state == GameServer.SquareState.E) {
                view?.text = ""
            } else {
                view?.text = state.toString()
            }
        }
    }

    private fun resetGridBackground() {
        displaySquareList.forEach{ square ->
            square?.setBackgroundColor(colorOfReset!!)
        }
    }

    private fun displayWinner(winner: GameServer.SquareState) {
        displayStatusMessage(String.format(getString(R.string.winner_message), winner.toString()))
    }

    private fun displayVictory(winSquares: List<Int>) {
        displaySquareList[winSquares[0]]?.setBackgroundColor(colorOfWin!!)
        displaySquareList[winSquares[1]]?.setBackgroundColor(colorOfWin!!)
        displaySquareList[winSquares[2]]?.setBackgroundColor(colorOfWin!!)
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
        GameServer.queueActivityMessage("p:$gridIndex")
    }

    private fun displayCurrPlayer(player: GameServer.SquareState) {
        (textPlayerView as TextView).text =
            String.format(getString(R.string.curr_player_message), player.toString())
    }

    private fun displayCurrPlayer_old(player: String) {
        (textPlayerView as TextView).text =
            String.format(getString(R.string.curr_player_message), player)
    }

    private fun displayStatusMessage(status: String) {
        textStatusView?.text = status
    }

    fun onClickNewGame(view: View) {
        // Ask user to confirm new game
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.new_game_title_message))
        builder.setMessage(getString(R.string.new_game_confirm_message))
        builder.setPositiveButton(getString(R.string.new_button_message)) { _, _ ->
            GameServer.queueActivityMessage("reset:")
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
    }

    fun onClickSettings(view: View) {
        // Switch to SettingsActivity
        val intent: Intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    fun onClickExitApp(view: View) {
        confirmExitApp()
    }

    private fun confirmExitApp() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.exit_title_message))
        builder.setMessage(getString(R.string.exit_confirm_message))
        builder.setPositiveButton(getString(R.string.exit_message)) { _, _ ->
            exitApp()
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
    }

    private fun exitApp() {
        stopGameServer()
        finishAndRemoveTask()
    }

    private fun enableMessagesFromGameServer() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(packageName + DISPLAY_MESSAGE_SUFFIX)
        registerReceiver(gameMessageReceiver, intentFilter)
        Log.d(TAG, "Enabled message receiver for [${packageName + DISPLAY_MESSAGE_SUFFIX}]")
    }

    private fun disableMessagesFromGameServer() {
        unregisterReceiver(gameMessageReceiver)
    }

    /**
        Receive messages from the GameServer.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val stateString = intent.getStringExtra("State")
            val turnFlag = intent.getBooleanExtra("YourTurn", false)

            if (stateString != null) {
                val newState = GameServer.decodeState(stateString)

                displayGrid(newState.grid)
                displayCurrPlayer(newState.currPlayer)
                if (newState.winner != GameServer.SquareState.E) {
                    displayWinner(newState.winner)
                } else {
                    if (turnFlag) {
                        displayStatusMessage(getString(R.string.your_turn_message))
                    } else {
                        displayStatusMessage(getString(R.string.waiting_for_message))
                    }
                }

                if (newState.winSquares == null) {
                    resetGridBackground()
                } else {
                    displayVictory(newState.winSquares)
                }
            }
        }
    }

    companion object {
        private val TAG = GameplayActivity::class.java.simpleName
        val DISPLAY_MESSAGE_SUFFIX = ".$TAG.display.UPDATE"
    }
}