package game.paulgross.tictactoe

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

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

    private val allPossibleWinSquares: List<List<Int>> = listOf(
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),

        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),

        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )

    private var displaySquares: MutableList<TextView?> = mutableListOf()

    // Colours
    private var colorReset: Int? = null
    private var colorWinning: Int? = null

    private var textPlayer: TextView? = null


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

    override fun onPause() {
        super.onPause()

        saveAppState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Add all the display squares into the list.
        // NOTE: The square MUST BE added in index order.
        displaySquares.add(findViewById(R.id.textSquare1))
        displaySquares.add(findViewById(R.id.textSquare2))
        displaySquares.add(findViewById(R.id.textSquare3))
        displaySquares.add(findViewById(R.id.textSquare4))
        displaySquares.add(findViewById(R.id.textSquare5))
        displaySquares.add(findViewById(R.id.textSquare6))
        displaySquares.add(findViewById(R.id.textSquare7))
        displaySquares.add(findViewById(R.id.textSquare8))
        displaySquares.add(findViewById(R.id.textSquare9))

        colorReset = getColor(R.color.green_pastel)
        colorWinning = getColor(R.color.orange_pastel)

        textPlayer = findViewById(R.id.textPlayer)

        restoreAppState()

        displayGrid()
        displayCurrPlayer()
        displayAnyWin()
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
     * Displays the current grid state using the View squares.
     */
    private fun displayGrid() {
        // TODO: Convert to iterator
        displaySquare(grid[0], displaySquares[0])
        displaySquare(grid[1], displaySquares[1])
        displaySquare(grid[2], displaySquares[2])
        displaySquare(grid[3], displaySquares[3])
        displaySquare(grid[4], displaySquares[4])
        displaySquare(grid[5], displaySquares[5])
        displaySquare(grid[6], displaySquares[6])
        displaySquare(grid[7], displaySquares[7])
        displaySquare(grid[8], displaySquares[8])
    }

    private fun displaySquare(state: SquareState, squareView: TextView?) {
        if (state == SquareState.E) {
            squareView?.text = ""
        } else {
            squareView?.text = state.toString()
        }
    }

    private fun resetGridDisplay() {
        displaySquares.forEach{ square ->
            square?.setBackgroundColor(colorReset!!)
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

            Toast.makeText(
                this,
                String.format(getString(R.string.winner_message), winner),
                Toast.LENGTH_SHORT
            ).show()
            return true
        }
        return false
    }

    private fun getWinningSquares(): List<Int>? {
        allPossibleWinSquares.forEach{ possibleWin ->
            if (grid[possibleWin[0]] != SquareState.E && grid[possibleWin[0]] == grid[possibleWin[1]] && grid[possibleWin[0]] == grid[possibleWin[2]]) {
                return possibleWin  // Found a winner
            }
        }
        return null
    }

    private fun displayVictory(winSquares: List<Int>) {
        displaySquares[winSquares[0]]?.setBackgroundColor(colorWinning!!)
        displaySquares[winSquares[1]]?.setBackgroundColor(colorWinning!!)
        displaySquares[winSquares[2]]?.setBackgroundColor(colorWinning!!)
    }

    /**
     *  Handler for when the User clicks a square in the playing grid.
     */
    fun onClickPlaySquare(view: View) {
        if (winner != SquareState.E) {
            return // No more moves after a win
        }

        val gridIndex = displaySquares.indexOf(view as TextView)
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
        displaySquare(currPlayer, view)

        val winnerFound = displayAnyWin()
        if (!winnerFound) {
            // Switch to next player
            currPlayer = if (currPlayer == SquareState.X) { SquareState.O } else { SquareState.X }
            displayCurrPlayer()
        }
    }

    private fun displayCurrPlayer() {
        (textPlayer as TextView).text =
            String.format(getString(R.string.curr_player_message), currPlayer)
    }

    private fun displayWinner(winner: String) {
        (textPlayer as TextView).text = String.format(getString(R.string.winner_message), winner)
    }

    fun onClickNewGame(view: View) {
        // Ask user to confirm new game
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