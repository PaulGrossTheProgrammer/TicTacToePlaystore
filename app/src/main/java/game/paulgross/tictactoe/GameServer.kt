package game.paulgross.tictactoe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameServer(applicationContext: Context, sharedPreferences: SharedPreferences) : Thread() {

    var socketServer: SocketServer? = null

    private val gameRequestQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()
    fun getRequestQueue(): BlockingQueue<ClientRequest> {
        return gameRequestQ
    }

    private val context: Context
    private val preferences: SharedPreferences
    init {
        // Store the context pointer to allow access to Intent message broadcast system.
        context = applicationContext
        preferences = sharedPreferences
    }

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
     */
    var grid: Array<SquareState> = arrayOf(
        SquareState.E, SquareState.E, SquareState.E,
        SquareState.E, SquareState.E, SquareState.E,
        SquareState.E, SquareState.E, SquareState.E
    )

    var currPlayer: SquareState = SquareState.X
    var winner = SquareState.E

    data class ClientRequest(val requestString: String, val responseQ: Queue<String>?)

    private val working = AtomicBoolean(true)

    override fun run() {
        socketServer = SocketServer(gameRequestQ)
        socketServer!!.start()

        restoreGameState()
        messageUIDisplayGrid()
        updateWinDisplay()

        while (working.get()) {
            // TODO - move this to a "server" function so that we can also have a "client" function.
            // TODO - clear at least a few client requests if there are many queued up.
            val request = gameRequestQ.poll()  // Non-blocking read for client requests.
            if (request != null) {
                val requestString = request.requestString
                val responseQ = request.responseQ

                Log.d(TAG, "[$requestString]")

                if (requestString == "exit") {
                    responseQ?.add("exit")
                    // TODO - allow other players to take over client's role ...
                } else {
                    var validRequest = false
                    if (requestString.startsWith("s:", true)!!) {
                        validRequest = true
                        val indexString = requestString[2].toString()
                        val gridIndex = Integer.valueOf(indexString)
                        playSquare(gridIndex)

                        responseQ?.add("g:${encodeGrid()}")  // Add curr player. On a new line???
                        messageUIDisplayGrid()
                    }
                    if (requestString == "status:") {
                        validRequest = true
                        responseQ?.add("g:${encodeGrid()}")
                    }
                    if (requestString == "display:") {
                        validRequest = true
                        // Forces the UI to display.
                        messageUIDisplayGrid()
                        updateWinDisplay()
                    }
                    if (requestString == "reset:") {
                        validRequest = true
                        resetGame()
                        messageUIDisplayGrid()
                    }

                    if (!validRequest) {
                        responseQ?.add("invalid:$requestString")
                    }
                }
            }
            Thread.sleep(100L)  // Pause for a short time...

            // If the game is also a player, then this is where the AI is coded.
        }
        Log.d(TAG, "The Game Server has shut down.")
    }

    fun queueClientRequest(request: String) {
        gameRequestQ.add(ClientRequest(request, null))
    }

    fun pauseApp() {
        // TODO - pause App mode while the MainActivity is suspended or being updated.
    }

    fun resumeApp() {
        // TODO - resume App.
    }

    fun pauseGame() {
        // TODO - put the game into pause mode.
    }

    fun resumeGame() {
        // TODO - resume game
    }

    fun shutdown() {
        Log.d(TAG, "The Game Server is shutting down ...")
        working.set(false)
        socketServer?.shutdown()
    }

    /**
     * Saves the current App state.
     *
     * MUST BE called from overridden onPause() to avoid accidental state loss.
     */
    private fun saveGameState() {
        val editor = preferences.edit()

        // Save the grid state.
        for (i in 0..8) {
            editor.putString("Grid$i", grid!![i].toString())
        }

        editor.putString("CurrPlayer", currPlayer.toString())

        editor.apply()
        Log.d(TAG, "Saved game state.")
    }

    /**
     * Restores the App state from the last time it was running.
     *
     * MUST BE called from onCreate().
     */
    private fun restoreGameState() {
        Log.d(TAG, "Restoring previous game state...")

        // Load the previous grid state. Default to Empty if nothing was saved before.
        for (i in 0..8) {
            val currState = preferences.getString("Grid$i", "E").toString()
            grid[i] = SquareState.valueOf(currState)
        }

        // If there is no current player to restore, default to "X"
        currPlayer = SquareState.valueOf(preferences.getString("CurrPlayer", "X").toString())
    }
    private fun messageUIDisplayGrid() {
        val intent = Intent()
        intent.action = context.packageName + "display.UPDATE"
        val gs = encodeGrid()
        Log.d(TAG, "About to send grid: [$gs]")
        intent.putExtra("grid", encodeGrid())
        intent.putExtra("player", currPlayer.toString())
        context.sendBroadcast(intent)
    }

    private fun messageUIDisplayVictory(winSquares: List<Int>) {
        val intent = Intent()
        intent.action = context.packageName + "display.UPDATE"
        val squares = winSquares[0].toString() + winSquares[1].toString() + winSquares[2].toString()
        intent.putExtra("winsquares", squares)
        context.sendBroadcast(intent)
    }

    private fun messageUIDisplayWinner(winner: String) {
        val intent = Intent()
        intent.action = context.packageName + "display.UPDATE"
        intent.putExtra("winner", winner)
        context.sendBroadcast(intent)
    }

    private fun encodeGrid(): String {
        var encoded = ""
        for (i in 0..8) {
            encoded += grid[i].toString()
        }
        return encoded
    }

    fun decodeGrid(gridString: String) {
        for (i in 0..8) {
            grid[i] = SquareState.valueOf(gridString[i].toString())
        }
    }

    fun resetGame() {
        for (i in 0..8) {
            grid[i] = SquareState.E
        }
        winner = SquareState.E
        currPlayer = SquareState.X
    }

    private fun playSquare(gridIndex: Int) {
        if (winner != SquareState.E) {
            return // No more moves after a win
        }

        if (grid[gridIndex] != SquareState.E) {
            return  // Can only change Empty squares
        }

        grid[gridIndex] = currPlayer

        val hasWinner = updateWinDisplay()
        if (!hasWinner) {
            // Switch to next player
            currPlayer = if (currPlayer == SquareState.X) {
                SquareState.O
            } else {
                SquareState.X
            }

            Log.d(TAG, "Current Player = $currPlayer")
        }

        saveGameState()
    }

    private fun updateWinDisplay(): Boolean {
        val winSquares: List<Int>? = getWinningSquares()
        if (winSquares != null) {
            winner = grid[winSquares[0]]
            messageUIDisplayVictory(winSquares)
            messageUIDisplayWinner(winner.toString())
            return true
        } else {
            return false
        }
    }

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
    private fun getWinningSquares(): List<Int>? {
        allPossibleWinCombinations.forEach{ possibleWin ->
            if (grid[possibleWin[0]] != SquareState.E
                &&
                grid[possibleWin[0]] == grid[possibleWin[1]]
                &&
                grid[possibleWin[0]] == grid[possibleWin[2]]) {
                return possibleWin  // Found a winner
            }
        }
        return null
    }

    companion object {
        private val TAG = GameServer::class.java.simpleName
    }
}