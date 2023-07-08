package game.paulgross.tictactoe

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameService : Service() {

    // TODO
    // The Socket Server thread creates client handler threads, passing the que to them too.
    // When a client needs work done, the request queue is added to by the client handler
    // This Service listens to the request queue, and interacts with the Activity as required.
    private val gameRequestQ: Queue<String> = ConcurrentLinkedQueue()

    private val working = AtomicBoolean(true)
    private val gameThread = Runnable {

        SocketServer(gameRequestQ).start()

        while (working.get()) {

            val clientData = gameRequestQ.poll()  // Non-blocking read for client requests.
            if (clientData != null) {
                Log.d(TAG, "[$clientData]")

                if (clientData == "exit") {
                    // TODO - remove queue because the client has closed the connection.
                } else {
                    val intent = Intent()
                    intent.action = packageName + "client.REQUEST"
                    intent.putExtra("Request", clientData)
                    sendBroadcast(intent)

                    // TODO - need to figure out how to query game state.
                    // Maybe store game state in a Companion Object here...?
                    // And figure out how the Activity calls this class.
//                    SquareState.O
//                    testComp()

                    // TODO - need the response queue to return state to client thread.

                }
            }
            Thread.sleep(100L)  // Pause for a while...

            // If the game is a player, then this is where the AI is coded.
        }
    }

    override fun onDestroy() {
        working.set(false)
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d(TAG, "Service is running onBind()...")
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Service is running onCreate()...")
        Thread(gameThread).start()
    }

    companion object {
        private val TAG = GameService::class.java.simpleName

        // Maybe put the state in here so that it can be queried from Activity???
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

        fun resetGame() {
            for (i in 0..8) {
                grid[i] = SquareState.E
            }
            winner = Companion.SquareState.E
            currPlayer = Companion.SquareState.X
        }
    }

}