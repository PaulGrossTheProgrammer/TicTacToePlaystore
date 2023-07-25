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

class GameplayActivity : AppCompatActivity() {

    private var displaySquareList: MutableList<TextView?> = mutableListOf()
    private var textPlayerView: TextView? = null

    private var colorOfWin: Int? = null
    private var colorOfReset: Int? = null

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

        enableMessagesFromGameServer()

        GameServer.activate(applicationContext, getPreferences(MODE_PRIVATE))
        GameServer.queueActivityRequest("resume:")
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
        GameServer.queueActivityRequest("StopGame")
    }

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
        clearGrid()
        resetGridBackground()
    }

    private fun clearGrid() {
        displaySquareList.forEach{ square ->
            square?.text = ""
        }
    }

    private fun resetGridBackground() {
        displaySquareList.forEach{ square ->
            square?.setBackgroundColor(colorOfReset!!)
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

        displaySquareList[s1]?.setBackgroundColor(colorOfWin!!)
        displaySquareList[s2]?.setBackgroundColor(colorOfWin!!)
        displaySquareList[s3]?.setBackgroundColor(colorOfWin!!)
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
        GameServer.queueActivityRequest("p:$gridIndex")
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
            GameServer.queueActivityRequest("reset:")
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
        // FIXME: The buttons on the AlertDialog have different colours to the layout.
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

            val resetFlag = intent.getBooleanExtra("reset", false)
            val clearBackgroundFlag = intent.getBooleanExtra("ClearBackground", false)
            val gridStateString = intent.getStringExtra("grid")
            val playerString = intent.getStringExtra("player")
            val winsquaresString = intent.getStringExtra("winsquares")
            val winnerString = intent.getStringExtra("winner")

            if (resetFlag) {
                resetGridDisplay()
            }
            if (clearBackgroundFlag) {
                resetGridBackground()
            }
            if (gridStateString != null) {
                displayGrid(gridStateString)
            }
            if (playerString != null) {
                displayCurrPlayer(playerString)
            }
            if (winsquaresString != null) {
                displayVictory(winsquaresString)
            }
            if (winnerString != null) {
                toastWinner(winnerString)
            }
        }
    }

    companion object {
        private val TAG = GameplayActivity::class.java.simpleName
        val DISPLAY_MESSAGE_SUFFIX = ".$TAG.display.UPDATE"
    }
}