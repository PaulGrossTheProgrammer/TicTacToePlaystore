package game.paulgross.tictactoe

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.util.Log
import java.lang.Exception
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class GameServer(applicationContext: Context, sharedPreferences: SharedPreferences): Thread() {

    private var socketServer: SocketServer? = null
    private var socketClient: SocketClient? = null

    private val working = AtomicBoolean(true)

    private val gameRequestQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()
    private val context: Context
    private val preferences: SharedPreferences

    init {
        context = applicationContext  // To access the Intent message broadcast system.
        preferences = sharedPreferences  // Used to load and save the game state.
    }

    enum class GameMode {
        /** Only responds to Activity requests. */
        LOCAL,

        /** Allow remote users to play using this GameServer. */
        SERVER,

        /** Connect to a network GameServer. */
        CLIENT
    }
    private var gameMode: GameMode = GameMode.LOCAL

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

    private var currPlayer: SquareState = SquareState.X
    private var winner = SquareState.E

    data class ClientRequest(val requestString: String, val responseQ: Queue<String?>?)

    private val allIpAddresses: MutableList<String> = mutableListOf()

    private fun determineIpAddresses() {
        allIpAddresses.clear()
        val cm: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork
        val lp = cm.getLinkProperties(n)
        val addrs = lp?.linkAddresses
        Log.d(TAG, "IP Address List:")
        addrs?.forEach { addr ->
            allIpAddresses.add(addr.address.hostAddress)
        }
    }

    override fun run() {
        // TODO: Move this IP Address code into function that listens to change of network state.
        determineIpAddresses()
        Log.d(TAG, "IP Address List: $allIpAddresses")

        restoreGameState()
        messageUIDisplayGrid()
        updateWinDisplay()

        while (working.get()) {
            // TODO - create a special queue for requests from Activity classes.
            val request = gameRequestQ.poll()  // Non-blocking read for client requests.
            var requestString: String? = null
            var responseQ: Queue<String?>? = null

            if (request != null) {
                requestString = request.requestString
                responseQ = request.responseQ
                if (requestString == "UpdateSettings") {
                    messageSettingsDisplayIpAddress(allIpAddresses)
                }
                if (requestString == "StartServer:") {
                    switchToLocalServerMode()
                }
                if (requestString == "StartLocal:") {
                    switchToPureLocalMode()
                }
                if (requestString.startsWith("RemoteServer:")) {
                    Log.d(TAG, "TODO: Switch to remote mode [$requestString]")
                    val ip = requestString.substringAfter(":", "")
                    if (ip != "") {
                        switchToRemoteServerMode(ip)
                    }
                }
                if (requestString == "shutdown:") {
                    shutdown()
                }
            }

            if (gameMode == GameMode.SERVER || gameMode == GameMode.LOCAL) {
                // TODO - clear at least a few client requests if there are many queued up.
                if (requestString != null) {
//                    Log.d(TAG, "Received Request: [$requestString]")

                    if (responseQ == null) {
                        // Local requests don't have a response Queue.
                        handleLocalRequest(requestString)
                    } else {
                        handleServerRequest(requestString, responseQ)
                    }

                }
            }

            if (gameMode == GameMode.CLIENT) {
                if (requestString != null) {
                    // Note that the client is going to periodically request status.
                    // So only UI actions need to be sent.
                    handleClientRequest(requestString, responseQ)
                }
            }

            // Adjust this period based on context. Fast for server, slower for client.
            // Slower for pause mode.
            sleep(200L)  // Pause for a short time...
        }
        Log.d(TAG, "The Game Server has shut down.")
    }

    fun getGameMode(): GameMode {
        return gameMode
    }

    private fun switchToRemoteServerMode(address: String) {
        Log.d(TAG, "Switch to Remote Server at: $address")
        if (socketServer != null) {
            socketServer?.shutdown()
            allIpAddresses.clear()
        }

        try {
            socketClient = SocketClient(address, SocketServer.PORT, gameRequestQ)
            socketClient!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Wait for success in both shutdown and startup
        gameMode = GameMode.CLIENT
    }

    private fun switchToLocalServerMode() {
        Log.d(TAG, "TODO: Switch to Local Server Mode.")
        if (socketClient != null) {
            socketClient?.shutdown()
        }

        socketServer = SocketServer(gameRequestQ)
        socketServer!!.start()
        determineIpAddresses()

        gameMode = GameMode.SERVER
    }

    private fun switchToPureLocalMode() {
        Log.d(TAG, "TODO: Switch to Local Server Mode.")
        if (socketServer != null) {
            socketServer?.shutdown()
            allIpAddresses.clear()
        }

        if (socketClient != null) {
            socketClient?.shutdown()
        }

        // FIXME - don't WAIT here - it will hold the main thread.
        // Instead, queue a request to change mode to enable main thread to proceed.
        gameMode = GameMode.LOCAL
    }

    var previousStateUpdate = ""

    private fun handleClientRequest(requestString: String, responseQ: Queue<String?>?) {
        if (requestString.startsWith("p:")) {
            socketClient?.messageFromGameServer(requestString)
        }

        if (requestString.startsWith("s:", true)) {
            val remoteState = requestString.substringAfter("s:")
            decodeGrid(remoteState.substring(0, 9))
            // FIXME: Need current player as well
            currPlayer = SquareState.valueOf(remoteState[9].toString())
            winner = SquareState.valueOf(remoteState[10].toString())

            if (previousStateUpdate != remoteState) {
                previousStateUpdate = remoteState
                Log.d(TAG, "Saving game stata.")
                saveGameState()
            }

            messageUIDisplayGrid()
        }
    }
    private fun handleLocalRequest(requestString: String) {
        if (requestString == "reset:") {
            resetGame()
            messageUIResetDisplay()
            messageUIDisplayGrid()
        }
        if (requestString == "resume:") {
            Log.d(TAG, "Handling LOCAL status ...")
            restoreGameState()
            messageUIDisplayGrid()
            updateWinDisplay()
        }
        if (requestString.startsWith("p:", true)) {
            val indexString = requestString[2].toString()
            val gridIndex = Integer.valueOf(indexString)
            playSquare(gridIndex)
            messageUIDisplayGrid()
        }
    }

    private fun handleServerRequest(requestString: String, responseQ: Queue<String?>?) {
//        Log.d(TAG, "handleServerRequest: [$requestString]")

        var validRequest = false
        if (requestString.startsWith("p:", true)) {
            validRequest = true
            val indexString = requestString[2].toString()
            val gridIndex = Integer.valueOf(indexString)
            playSquare(gridIndex)

            responseQ?.add("s:${encodeGrid()}$currPlayer$winner")  // TODO: Change to encode status
            messageUIDisplayGrid()
        }
        if (requestString == "status:") {
            validRequest = true
            responseQ?.add("s:${encodeGrid()}$currPlayer$winner")
        }
        if (requestString == "exit:") {
            validRequest = true
            responseQ?.add("shutdown")
            // TODO - allow other players to take over client's role ...
        }

        if (!validRequest) {
            Log.d(TAG, "invalid request: [$requestString]")
            responseQ?.add("invalid:$requestString")
        }
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

        if (gameMode == GameMode.SERVER) {
            socketServer?.shutdown()
        }

        // TODO - shut down client when in CLIENT mode.
        singletonGameServer = null
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
            editor.putString("Grid$i", grid[i].toString())
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

    private fun messageUIResetDisplay() {
        val intent = Intent()
        intent.action = context.packageName + MainActivity.DISPLAY_MESSAGE_SUFFIX
        intent.putExtra("reset", true)
        context.sendBroadcast(intent)
    }

    private fun messageSettingsDisplayIpAddress(addrList: List<String>) {
        var listAsString = "Server not running"
        if (gameMode == GameMode.SERVER) {
            listAsString = ""
            addrList.forEach { addr ->
                if (!listAsString.isEmpty()) {
                    listAsString += ", "
                }
                listAsString += addr
            }
        }
        val intent = Intent()
        intent.action = context.packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX
        intent.putExtra("IpAddressList", listAsString)
        context.sendBroadcast(intent)
    }
    private fun messageUIDisplayGrid() {
        val intent = Intent()
        intent.action = context.packageName + MainActivity.DISPLAY_MESSAGE_SUFFIX
        val gs = encodeGrid()
        Log.d(TAG, "About to send grid: [$gs]")
        intent.putExtra("grid", encodeGrid())
        intent.putExtra("player", currPlayer.toString())
        context.sendBroadcast(intent)
    }

    private fun messageUIDisplayVictory(winSquares: List<Int>) {
        val intent = Intent()
        intent.action = context.packageName + MainActivity.DISPLAY_MESSAGE_SUFFIX
        val squares = winSquares[0].toString() + winSquares[1].toString() + winSquares[2].toString()
        intent.putExtra("winsquares", squares)
        context.sendBroadcast(intent)
    }

    private fun messageUIDisplayWinner(winner: String) {
        val intent = Intent()
        intent.action = context.packageName + MainActivity.DISPLAY_MESSAGE_SUFFIX
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
        saveGameState()
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

        // The GameServer always runs in it's own thread, and shutdown() must be called as the App closes.
        @SuppressLint("StaticFieldLeak")
        private var singletonGameServer: GameServer? = null

        fun activate(applicationContext: Context, sharedPreferences: SharedPreferences) {
            if (singletonGameServer == null) {
                Log.d(TAG, "Starting new GameServer ...")
                singletonGameServer = GameServer(applicationContext, sharedPreferences)
                singletonGameServer!!.start()
            } else {
                Log.d(TAG, "Already created GameServer.")
            }
        }

        fun queueClientRequest(request: String) {
            if (singletonGameServer?.working!!.get()) {
                singletonGameServer?.gameRequestQ?.add(ClientRequest(request, null))
            }
        }
    }
}