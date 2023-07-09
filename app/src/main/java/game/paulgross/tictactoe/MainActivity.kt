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


class MainActivity : AppCompatActivity() {

    private val ENABLE_SOCKET_SERVER = true

    private var displaySquareList: MutableList<TextView?> = mutableListOf()

    private var colorOfReset: Int? = null
    private var colorOfWinning: Int? = null

    private var textPlayerView: TextView? = null

    /**
     * Dump out the current grid state into the debug log.
     */
/*    private fun debugGrid() {
        Log.d("DEBUG", "GRID:")
        Log.d("DEBUG", "${grid[0]} ${grid[1]} ${grid[2]}")
        Log.d("DEBUG", "${grid[3]} ${grid[4]} ${grid[5]}")
        Log.d("DEBUG", "${grid[6]} ${grid[7]} ${grid[8]}")
    }*/

    /**
     * Saves the current App state.
     *
     * MUST BE called from overridden onPause() to avoid accidental state loss.
     */
    private fun saveAppState() {
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()

        // Save the grid state.
        for (i in 0..8) {
            editor.putString("Grid$i", gameThread?.grid!![i]?.toString())
        }

        editor.putString("CurrPlayer", gameThread?.currPlayer.toString())

        editor.apply()
    }

    /**
     * Restores the App state from the last time it was running.
     *
     * MUST BE called from onCreate().
     */
    private fun restoreAppState() {
        Log.d("DEBUG", "Restoring previous game state...")
        val preferences = getPreferences(MODE_PRIVATE)

        // Load the previous grid state. Default to Empty if nothing was saved before.
        for (i in 0..8) {
            val currState = preferences.getString("Grid$i", "E").toString()
            gameThread?.setGridSquare(i, currState)
//            gameThread?.setGrid(i, "E")  // TEMPORARY to force a reset...
        }
//        debugGrid()

        // If there is no current player to restore, default to "X"
        val savedPlayer = preferences.getString("CurrPlayer", "X").toString()
        GameService.SquareState.valueOf(savedPlayer)
        gameThread?.currPlayer = GameService.SquareState.valueOf(savedPlayer)
    }

    private var appPaused = false

    override fun onPause() {
        unregisterReceiver(gameMessageReceiver)
        super.onPause()

        appPaused = true
        // TODO: Because screen rotation calls onPause()...
        //  ... decide how best to handle open sockets.
        //  Should sockets be closed later in the lifecycle?
        //  Perhaps the socket threads are created in onStart() and destroyed in onStop()?
        //  But if the App is being destroyed by the system, onStop() is never called...?
        //  Will the system clean up the open comms sockets?
        //  If we store the list of sockets we can still close the server thread
        //  and when onCreate is called again the list should still be valid.
        //  Maybe track "paused" flag so clients temporarily pause comms???
        //  What happens if the user makes a move during the rotate pause?
        //  Clients need to be sent the PAUSED state
        //  In PAUSED state no new client GUI actions can be started until PAUSED clears.
        //  In PAUSED state Server stops processing the client queue.
        //  After leaving PAUSED state, server resumes queue processing.
        //  NOTE that the client queue is timestamped, and old client actions are removed.
        //  So if the onPause is quickly resumed, the client will likely never notice.
        if (ENABLE_SOCKET_SERVER) {
            Log.d("DEBUG", "TODO: Pause or destroy the socket server.")
        }

        saveAppState()
    }

    private var gameThread: GameService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_landscape)
        } else {
            //The default layout is portrait
            setContentView(R.layout.activity_main)
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
        colorOfWinning = getColor(R.color.orange_pastel)

        textPlayerView = findViewById(R.id.textPlayer)

        restoreAppState()
        displayCurrPlayer(gameThread?.currPlayer.toString())
//        displayAnyWin()

        // TODO: Move this to after the UI is setup.
        appPaused = false  // Perhaps to re-enable paused sockets???
        if (ENABLE_SOCKET_SERVER) {
            Log.d("DEBUG", "Starting the socket server.")
            if (gameThread == null) {
                gameThread = GameService(applicationContext)
                gameThread?.start()
            }

            val intentFilter = IntentFilter()
            intentFilter.addAction(packageName + "display.UPDATE")
            registerReceiver(gameMessageReceiver, intentFilter)

        } else {
            Log.d("DEBUG", "Socket server DISABLED.")
        }


        // TODO - Send the grid state as a queued a client request update???
    }

    override fun onStop() {
        super.onStop()

        stopService(Intent(applicationContext, GameService::class.java))
    }

/*    private fun playSquare(gridIndex: Int) {
        // TODO - return true/false for change made
        // TODO - call this from local GUI and from client socket handler

        if (GameService.winner != GameService.Companion.SquareState.E) {
            return // No more moves after a win
        }

        if (GameService.grid[gridIndex] != GameService.Companion.SquareState.E) {
            return  // Can only change Empty squares
        }

        // TODO: Update the square's state - replace with GameServer objects.
        GameService.Companion.grid[gridIndex] = GameService.currPlayer

        displayGrid()

        val winnerFound = displayAnyWin()
        if (winnerFound) {
            toastWinner()
        } else {
            // Switch to next player
            GameService.currPlayer = if (GameService.currPlayer == GameService.Companion.SquareState.X) {
                GameService.Companion.SquareState.O
            } else {
                GameService.Companion.SquareState.X
            }
            displayCurrPlayer(GameService.currPlayer.toString())
        }
    }*/

    /*
        User Interface functions start here.
     */


    /**
     * Updates the current grid state into the display squares.
     */
    private fun displayGrid(gridStateString: String?) {
        for (i in 0..8) {
            val state = GameService.SquareState.valueOf(gridStateString?.get(i).toString())
            val view = displaySquareList[i]

            if (state == GameService.SquareState.E) {
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

    /**
     * Looks for and displays any winner in the game.
     * Includes a pop-up winning message.
     *
     * @return: true if a winner was found
     */
/*    private fun displayAnyWin(): Boolean {
        val winSquares: List<Int>? = GameService.getWinningSquares()
        if (winSquares != null) {
            GameService.winner = GameService.grid[winSquares[0]]
            displayWinner(GameService.winner.toString())
            displayVictory(winSquares)
            return true
        }
        return false
    }*/
    private fun toastWinner(winner: String) {
        Toast.makeText(
            this,
            String.format(getString(R.string.winner_message), winner),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun displayVictory(winSquares: List<Int>) {
        displaySquareList[winSquares[0]]?.setBackgroundColor(colorOfWinning!!)
        displaySquareList[winSquares[1]]?.setBackgroundColor(colorOfWinning!!)
        displaySquareList[winSquares[2]]?.setBackgroundColor(colorOfWinning!!)
    }

    /**
     *  Handler for when the User clicks a square in the playing grid.
     */
    fun onClickPlaySquare(view: View) {
        // TODO - integrate properly with playSquare()
        if (gameThread?.winner != GameService.SquareState.E) {
            return // No more moves after a win
        }

        val gridIndex = displaySquareList.indexOf(view as TextView)
        if (gridIndex == -1) {
            Log.d("Debug", "The user did NOT click a grid square.")
            return
        }

        if (gameThread?.grid?.get(gridIndex) != GameService.SquareState.E) {
            return  // Can only change Empty squares
        }

        // Update the square's state
        gameThread?.grid!![gridIndex] = gameThread?.currPlayer!!

        // Update the square's display
        view.text = gameThread?.currPlayer.toString()


/*        val winnerFound = displayAnyWin()
        if (winnerFound) {
            toastWinner()
        } else {
            // Switch to next player
            GameService.currPlayer = if (GameService.currPlayer == GameService.Companion.SquareState.X) {
                GameService.Companion.SquareState.O
            } else {
                GameService.Companion.SquareState.X
            }
            displayCurrPlayer(GameService.currPlayer.toString())
        }*/
    }

    private fun displayCurrPlayer(player: String) {
        (textPlayerView as TextView).text =
            String.format(getString(R.string.curr_player_message), player)
    }

    private fun displayWinner(winner: String) {
        (textPlayerView as TextView).text = String.format(getString(R.string.winner_message), winner)
    }

    fun onClickNewGame(view: View) {
        // Ask user to confirm new game
        // FIXME: The buttons on the AlertDialog have different colours to the layout.
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.new_game_title_message))
        builder.setMessage(getString(R.string.new_game_confirm_message))
        builder.setPositiveButton(getString(R.string.new_button_message)) { _, _ ->
            gameThread?.resetGame()
            resetGridDisplay()
            displayCurrPlayer(gameThread?.currPlayer.toString())
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
            finishAndRemoveTask()
        }
        builder.setNegativeButton(getString(R.string.go_back_message)) { _, _ -> }
        builder.show()
    }

    /**
        Receive messages from socket client handler.
     */
    private val gameMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // TODO: Extract grid state from Intent
            val gridStateString = intent.getStringExtra("grid")
            val playerString = intent.getStringExtra("player")
            Log.d("DEBUG", "Received current grid State = [$gridStateString]")
            displayGrid(gridStateString)
            if (playerString != null) {
                displayCurrPlayer(playerString)
            }

            // TODO - accept other update requests besides displayGrid()
            // Use putExtra() in the Service and getStringExtra() here to decide what to display

            /*val request = intent.getStringExtra("Request")
            Log.d("DEBUG_RECV", "Request =[$request]")

            // TODO - check that client player matches current player

            if (request?.startsWith("PLAY", true)!!) {
                val indexString = request.substring(4..4)
                val gridIndex = Integer.valueOf(indexString)
                playSquare(gridIndex)
            }*/
        }
    }
}