package game.paulgross.tictactoe

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.util.Scanner
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private val ENABLE_SOCKET_SERVER = true

    /**
     * All the possible states for a square.
     */
    enum class SquareState {
        /** Empty square */
        E,

        /** "X" square  */
        X,

        /** "O" square */
        O
    }

    /**
     * The playing grid.
     *
     * Initially empty.
     */
    private var grid: Array<SquareState> = arrayOf(
        SquareState.E, SquareState.E, SquareState.E,
        SquareState.E, SquareState.E, SquareState.E,
        SquareState.E, SquareState.E, SquareState.E
    )

    // State variables
    private var currPlayer: SquareState = SquareState.X
    private var winner = SquareState.E

    private val allPossibleWinCombinations: List<List<Int>> = listOf(
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),

        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),

        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )

    private var displaySquareList: MutableList<TextView?> = mutableListOf()

    private var colorOfReset: Int? = null
    private var colorOfWinning: Int? = null

    private var textPlayerView: TextView? = null


    /**
     * Dump out the current grid state into the debug log.
     */
    private fun debugGrid() {
        Log.d("DEBUG", "GRID:")
        Log.d("DEBUG", "${grid[0]} ${grid[1]} ${grid[2]}")
        Log.d("DEBUG", "${grid[3]} ${grid[4]} ${grid[5]}")
        Log.d("DEBUG", "${grid[6]} ${grid[7]} ${grid[8]}")
    }

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
            editor.putString("Grid$i", grid[i].toString())
        }

        editor.putString("CurrPlayer", currPlayer.toString())

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
            grid[i] = SquareState.valueOf(currState)
        }
        debugGrid()

        // If there is no current player to restore, default to "X"
        currPlayer = SquareState.valueOf(preferences.getString("CurrPlayer", "X").toString())
    }

    private var appPaused = false

    override fun onPause() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPaused = false  // Perhaps to re-enable paused sockets???
        if (ENABLE_SOCKET_SERVER) {
            Log.d("DEBUG", "Starting the socket server.")

            var service = startService(Intent(applicationContext, SocketServer::class.java))

        } else {
            Log.d("DEBUG", "Socket server DISABLED.")
        }

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

        displayGrid()
        displayCurrPlayer()
        displayAnyWin()
    }

    override fun onStop() {
        super.onStop()

        stopService(Intent(applicationContext, SocketServer::class.java))
    }

    private fun resetGridState() {
        for (i in 0..8) {
            grid[i] = SquareState.E
        }
    }

    private fun resetGame() {
        resetGridState()
        resetGridDisplay()
        winner = SquareState.E
        currPlayer = SquareState.X
        displayCurrPlayer()
    }


    /*
        User Interface functions start here.
     */


    /**
     * Updates the current grid state into the display squares.
     */
    private fun displayGrid() {
        for (i in 0..8) {
            val state = grid[i]
            val view = displaySquareList[i]

            if (state == SquareState.E) {
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
    private fun displayAnyWin(): Boolean {
        val winSquares: List<Int>? = getWinningSquares()
        if (winSquares != null) {
            winner = grid[winSquares[0]]
            displayWinner(winner.toString())
            displayVictory(winSquares)
            return true
        }
        return false
    }
    private fun toastWinner() {
        Toast.makeText(
            this,
            String.format(getString(R.string.winner_message), winner),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getWinningSquares(): List<Int>? {
        allPossibleWinCombinations.forEach{ possibleWin ->
            if (grid[possibleWin[0]] != SquareState.E && grid[possibleWin[0]] == grid[possibleWin[1]] && grid[possibleWin[0]] == grid[possibleWin[2]]) {
                return possibleWin  // Found a winner
            }
        }
        return null
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
        if (winner != SquareState.E) {
            return // No more moves after a win
        }

        val gridIndex = displaySquareList.indexOf(view as TextView)
        if (gridIndex == -1) {
            Log.d("Debug", "The user did NOT click a grid square.")
            return
        }

        if (grid[gridIndex] != SquareState.E) {
            return  // Can only change Empty squares
        }

        // Update the square's state
        grid[gridIndex] = currPlayer

        // Update the square's display
        view.text = currPlayer.toString()

        val winnerFound = displayAnyWin()
        if (winnerFound) {
            toastWinner()
        } else {
            // Switch to next player
            currPlayer = if (currPlayer == SquareState.X) { SquareState.O } else { SquareState.X }
            displayCurrPlayer()
        }
    }

    private fun displayCurrPlayer() {
        (textPlayerView as TextView).text =
            String.format(getString(R.string.curr_player_message), currPlayer)
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
            resetGame()
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

}