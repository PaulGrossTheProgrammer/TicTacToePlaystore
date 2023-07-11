package game.paulgross.tictactoe

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameServer(applicationContext: Context) : Thread() {

    var socketServer: SocketServer? = null
    // TODO
    // The Socket Server thread creates client handler threads, passing the que to them too.
    // When a client needs work done, the request queue is added to by the client handler
    // This Service listens to the request queue, and interacts with the Activity as required.
    private val gameRequestQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()
    fun getRequestQueue(): BlockingQueue<ClientRequest> {
        return gameRequestQ
    }

    private val context: Context
    init {
        // Store the context pointer to allow access to Intent message broadcast system.
        context = applicationContext
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

        while (working.get()) {

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

                        responseQ?.add("g:${encodeGrid()}")
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

    private fun pause() {
        // TODO - pause while the MainActivity is suspended or being updated.
    }

    fun shutdown() {
        Log.d(TAG, "The Game Server is shutting down ...")
        working.set(false)
        socketServer?.shutdown()
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
    }

    private fun updateWinDisplay(): Boolean {
        Log.d(TAG, "Checking for win...")
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