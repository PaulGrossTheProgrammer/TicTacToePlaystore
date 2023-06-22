package game.paulgross.tictactoe

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    // State variables
    private var currPlayer = "X"
    private var winner = ""

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

        // Pointers to the display View squares
    private var square1: TextView? = null
    private var square2: TextView? = null
    private var square3: TextView? = null
    private var square4: TextView? = null
    private var square5: TextView? = null
    private var square6: TextView? = null
    private var square7: TextView? = null
    private var square8: TextView? = null
    private var square9: TextView? = null

    private var allDisplaySquares: MutableList<TextView?> = mutableListOf()

    // Colours
    private var colorReset: Int? = null
    private var colorWinning: Int? = null

    private var textPlayer: TextView? = null

    /**
     * Lookup function for determining the grid index for each screen View square.
     */
    private fun viewLookupGridIndex(view: View): Int? {
        when (view) {
            square1 -> return 0
            square2 -> return 1
            square3 -> return 2
            square4 -> return 3
            square5 -> return 4
            square6 -> return 5
            square7 -> return 6
            square8 -> return 7
            square9 -> return 8
        }
        return null  // That view isn't a grid square
    }

    /**
     * Lookup function for determining the screen View square for each grid index.
     */
    private fun gridIndexLookupView(index: Int): View? {
        when (index) {
            0 -> return square1
            1 -> return square2
            2 -> return square3
            3 -> return square4
            4 -> return square5
            5 -> return square6
            6 -> return square7
            7 -> return square8
            8 -> return square9
        }
        return null  // Invalid grid index
    }

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

        editor.putString("CurrPlayer", currPlayer)

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
        currPlayer = preferences.getString("CurrPlayer", "X").toString()
    }

    override fun onPause() {
        super.onPause()

        saveAppState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        square1 = findViewById(R.id.textSquare1)
        square2 = findViewById(R.id.textSquare2)
        square3 = findViewById(R.id.textSquare3)
        square4 = findViewById(R.id.textSquare4)
        square5 = findViewById(R.id.textSquare5)
        square6 = findViewById(R.id.textSquare6)
        square7 = findViewById(R.id.textSquare7)
        square8 = findViewById(R.id.textSquare8)
        square9 = findViewById(R.id.textSquare9)

        allDisplaySquares.add(square1)
        allDisplaySquares.add(square2)
        allDisplaySquares.add(square3)
        allDisplaySquares.add(square4)
        allDisplaySquares.add(square5)
        allDisplaySquares.add(square6)
        allDisplaySquares.add(square7)
        allDisplaySquares.add(square8)
        allDisplaySquares.add(square9)

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
        winner = ""
        currPlayer = "X"
        displayCurrPlayer()
    }


    /*
        User Interface functions start here.
     */


    /**
     * Displays the current grid state using the View squares.
     */
    private fun displayGrid() {
        displaySquare(grid[0], square1)
        displaySquare(grid[1], square2)
        displaySquare(grid[2], square3)
        displaySquare(grid[3], square4)
        displaySquare(grid[4], square5)
        displaySquare(grid[5], square6)
        displaySquare(grid[6], square7)
        displaySquare(grid[7], square8)
        displaySquare(grid[8], square9)
    }

    private fun displaySquare(state: SquareState, squareView: TextView?) {
        if (state == SquareState.E) {
            squareView?.text = ""
        } else {
            squareView?.text = state.toString()
        }
    }

    private fun resetGridDisplay() {
        allDisplaySquares.forEach{ square ->
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
            winner = grid[winSquares[0]].toString()
            displayWinner(winner)
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
        gridIndexLookupView(winSquares[0])?.setBackgroundColor(colorWinning!!)
        gridIndexLookupView(winSquares[1])?.setBackgroundColor(colorWinning!!)
        gridIndexLookupView(winSquares[2])?.setBackgroundColor(colorWinning!!)
    }

    /**
     *  Handler for when the User clicks a square in the playing grid.
     */
    fun onClickPlaySquare(view: View) {
        if (winner != "") {
            return // No more moves after a win
        }

        val gridIndex = viewLookupGridIndex(view as TextView)
        if (gridIndex == null) {
            Log.d("Debug", "The user did NOT click a grid square.")
            return
        }

        if (grid[gridIndex] != SquareState.E) {
            return  // Can only change Empty squares
        }

        // Update the square's state
        var newState = SquareState.E
        when (currPlayer) {
            "O" -> { newState = SquareState.O }
            "X" -> { newState = SquareState.X }
        }
        grid[gridIndex] = newState

        // Update the screen
        displaySquare(newState, view)

        val winnerFound = displayAnyWin()
        if (!winnerFound) {
            // Switch to next player
            when (currPlayer) {
                "O" -> { currPlayer = "X" }
                "X" -> { currPlayer = "O" }
            }
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