package game.paulgross.tictactoe

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameService(applicationContext: Context) : Thread() {

    private val context: Context
    init {
        // keep the context for Intent broadcasts
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

    // TODO
    // The Socket Server thread creates client handler threads, passing the que to them too.
    // When a client needs work done, the request queue is added to by the client handler
    // This Service listens to the request queue, and interacts with the Activity as required.
    private val gameRequestQ: Queue<String> = ConcurrentLinkedQueue()

    private val working = AtomicBoolean(true)

    override fun run() {
        SocketServer(gameRequestQ).start()

        while (working.get()) {

            val request = gameRequestQ.poll()  // Non-blocking read for client requests.
            if (request != null) {
                Log.d(TAG, "[$request]")

                if (request == "exit") {
                    // TODO - remove client response queue because the client has closed the connection.
                } else {
                    if (request?.startsWith("PLAY", true)!!) {
                        val indexString = request.substring(4..4)
                        val gridIndex = Integer.valueOf(indexString)
                        playSquare(gridIndex)
                    }

                    // Tell the UI to update
                    val intent = Intent()
                    intent.action = context.packageName + "display.UPDATE"
                    context.sendBroadcast(intent)

                    // TODO - need the response queue to return state to client thread.

                }
            }
            Thread.sleep(100L)  // Pause for a while...

            // If the game is a player, then this is where the AI is coded.
        }
    }

    fun shutdown() {
        working.set(false)
    }

    fun resetGame() {
        for (i in 0..8) {
            grid[i] = SquareState.E
        }
        winner = SquareState.E
        currPlayer = SquareState.X
    }

    fun setGrid(index: Int, stateString: String) {
        grid[index] = SquareState.valueOf(stateString)
    }
    private fun playSquare(gridIndex: Int) {
        // TODO - return true/false for change made
        // TODO - call this from local GUI and from client socket handler

        if (winner != SquareState.E) {
            return // No more moves after a win
        }

        if (grid[gridIndex] != GameService.SquareState.E) {
            return  // Can only change Empty squares
        }

        // TODO: Update the square's state - replace with GameServer objects.
        grid[gridIndex] = currPlayer

//        displayGrid()

        val winSquares: List<Int>? = getWinningSquares()
//        val winnerFound = displayAnyWin()
        if (winSquares == null) {
//            toastWinner()
        } else {
            // Switch to next player
            currPlayer = if (currPlayer == SquareState.X) {
                SquareState.O
            } else {
                SquareState.X
            }
//            displayCurrPlayer(GameService.currPlayer.toString())
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
    fun getWinningSquares(): List<Int>? {
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