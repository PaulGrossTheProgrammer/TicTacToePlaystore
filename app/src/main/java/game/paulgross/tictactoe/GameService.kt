package game.paulgross.tictactoe

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameService(applicationContext: Context) : Thread() {

    private val context: Context
    init {
        // Store the context pointer to access Intent broadcasts
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

    // Maybe the responseQ can be option, say when the local client doesn't need it???
    data class ClientRequest(val requestString: String, val responseQ: Queue<String>)

    // TODO
    // The Socket Server thread creates client handler threads, passing the que to them too.
    // When a client needs work done, the request queue is added to by the client handler
    // This Service listens to the request queue, and interacts with the Activity as required.
    private val gameRequestQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()

    private val working = AtomicBoolean(true)

    override fun run() {
        // TODO - make sure we aren't running a thread already...
        SocketServer(gameRequestQ).start()

        while (working.get()) {

            // TODO - clear at least a few client requests if there are many queued up.
            val request = gameRequestQ.poll()  // Non-blocking read for client requests.
            if (request != null) {
                val requestString = request.requestString
                val responseQ = request.responseQ

                Log.d(TAG, "Got a response Queue: $responseQ")

                Log.d(TAG, "[$request]")

                if (requestString == "exit") {
                    responseQ.add("exit")
                } else {
                    var update = false
                    if (requestString.startsWith("s:", true)!!) {
                        val indexString = requestString[2].toString()
                        val gridIndex = Integer.valueOf(indexString)
                        playSquare(gridIndex)

                        responseQ.add("g:${encodeGrid()}")
                        update = true
                    }

                    if (update) {
                        // Tell the UI to update
                        val intent = Intent()
                        intent.action = context.packageName + "display.UPDATE"
                        val gs = encodeGrid()
                        Log.d(TAG, "About to send grid: [$gs]")
                        intent.putExtra("grid", encodeGrid())
                        intent.putExtra("player", currPlayer.toString())
                        context.sendBroadcast(intent)
                        Log.d(TAG, "Intent was sent.")
                    }

                    // TODO - need the response queue to return state to client thread.

                }
            }
            Thread.sleep(100L)  // Pause for a while...

            // If the game is also a player, then this is where the AI is coded.
        }
    }

    fun shutdown() {
        working.set(false)
    }

    private fun encodeGrid(): String {
        var encoded = ""
        for (i in 0..8) {
            encoded += grid[i].toString()
        }
        return encoded
    }

    fun resetGame() {
        for (i in 0..8) {
            grid[i] = SquareState.E
        }
        winner = SquareState.E
        currPlayer = SquareState.X
    }

    fun setGridSquare(index: Int, stateString: String) {
        grid[index] = SquareState.valueOf(stateString)
    }

    private fun playSquare(gridIndex: Int) {
        // TODO - return true if a change made.
        // TODO - call this from local GUI and from client socket handler

        if (winner != SquareState.E) {
            return // No more moves after a win
        }

        if (grid[gridIndex] != SquareState.E) {
            return  // Can only change Empty squares
        }

        // TODO: Update the square's state - replace with GameServer objects.
        grid[gridIndex] = currPlayer

        Log.d(TAG, "Checking for win...")
        val winSquares: List<Int>? = getWinningSquares()
        if (winSquares != null) {
//            toastWinner()
        } else {
            // Switch to next player
            currPlayer = if (currPlayer == SquareState.X) {
                SquareState.O
            } else {
                SquareState.X
            }

            Log.d(TAG, "Current Player = $currPlayer")
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
        private val TAG = GameService::class.java.simpleName
    }
}