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

class GameServer(private val context: Context, private val preferences: SharedPreferences): Thread() {

    private var socketServer: SocketServer? = null
    private var socketClient: SocketClient? = null

    private val gameIsRunning = AtomicBoolean(true)  // TODO - this might not be needed.

    private val fromClientHandlerToGameServerQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()
    private val fromClientToGameServerQ: BlockingQueue<ClientRequest> = LinkedBlockingQueue()
    private val fromActivitiesToGameSeverQ: BlockingQueue<String> = LinkedBlockingQueue()

    enum class GameMode {
        /** Game only responds to local Activity requests. */
        LOCAL,

        /** Allow remote users to play by joining this GameServer over the network. */
        SERVER,

        /** Joined a network GameServer. */
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
    private var grid: Array<SquareState> = Array(9) {SquareState.E}

    private var currPlayer: SquareState = SquareState.X
    private var winner = SquareState.E

    private var remotePlayers: MutableMap<Queue<String>, SquareState> = mutableMapOf()  // Only used in SERVER mode.
    private var clientPlayer = SquareState.E  // EMPTY unless in CLIENT mode.

    data class ClientRequest(val requestString: String, val responseQ: Queue<String>)

    private val allIpAddresses: MutableList<String> = mutableListOf()

    private fun determineIpAddresses() {
        // FUTURE: Need to monitor the network and react to IP address changes.
        allIpAddresses.clear()
        val cm: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val addrs = lp?.linkAddresses
        addrs?.forEach { addr ->
            allIpAddresses.add(addr.address.hostAddress)
        }
    }

    private val loopDelayMilliseconds = 25L
    private val autoStatusDelayMilliseconds = 5000L
    private val autoStatusCount = autoStatusDelayMilliseconds.div(loopDelayMilliseconds)
    private var autoStatusCountdown = 0L

    override fun run() {

        restoreGameState()

        while (gameIsRunning.get()) {
            val activityRequest = fromActivitiesToGameSeverQ.poll()  // Non-blocking read.
            if (activityRequest != null) {
                handleActivityMessage(activityRequest)
            }

            val clientHandlerMessage = fromClientHandlerToGameServerQ.poll()  // Non-blocking read.
            if (clientHandlerMessage != null) {
                // TODO - clear at least a few client requests if there are many queued up.
                handleClientHandlerMessage(clientHandlerMessage.requestString, clientHandlerMessage.responseQ)
            }

            val clientMessage = fromClientToGameServerQ.poll()  // Non-blocking read.
            if (clientMessage != null) {
                handleClientMessage(clientMessage.requestString, clientMessage.responseQ)
            }

            if (gameMode == GameMode.CLIENT) {
                // Automatically request a new status after a delay
                autoStatusCountdown--
                if (autoStatusCountdown < 1) {
                    autoStatusCountdown = autoStatusCount
                    socketClient?.messageFromGameServer("status:")
                }
            }

            sleep(loopDelayMilliseconds)  // Pause for a short time...
        }
        Log.d(TAG, "The Game Server has shut down.")
    }

    fun getGameMode(): GameMode {
        return gameMode
    }

    private fun switchToRemoteServerMode(address: String) {
        // FIXME - doesn't handle when the remote server isn't running...

        Log.d(TAG, "Switch to Remote Server at: $address")
        if (socketServer != null) {
            socketServer?.shutdownRequest()
            allIpAddresses.clear()
            socketServer = null
            remotePlayers.clear()
        }

        autoStatusCountdown = 0
        try {
            socketClient = SocketClient(address, SocketServer.PORT)
            socketClient!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        gameMode = GameMode.CLIENT
    }

    private fun switchToLocalServerMode() {
        if (socketClient != null) {
            socketClient?.shutdownRequest()
            socketClient = null
        }

        remotePlayers.clear()
        socketServer = SocketServer(fromClientHandlerToGameServerQ)
        socketServer!!.start()
        determineIpAddresses()

        gameMode = GameMode.SERVER
    }

    private fun switchToPureLocalMode() {
        if (socketServer != null) {
            socketServer?.shutdownRequest()
            allIpAddresses.clear()
            socketServer = null
            remotePlayers.clear()
        }

        if (socketClient != null) {
            socketClient?.shutdownRequest()
            socketClient = null
        }

        gameMode = GameMode.LOCAL
    }

    private var previousStateUpdate = ""

    private fun handleClientHandlerMessage(message: String, responseQ: Queue<String>) {

        var validRequest = false
        if (message == "Initialise") {
            validRequest = true
            // Is there a spare player?
            if (remotePlayers.size < 2) {
                // Is the current player available?
                if (!remotePlayers.containsValue(currPlayer)) {
                    // Allocate the current player to this client
                    remotePlayers[responseQ] = currPlayer
                } else {
                    // Allocate the alternative player
                    if (currPlayer == SquareState.X) {
                        remotePlayers[responseQ] = SquareState.O
                    } else {
                        remotePlayers[responseQ] = SquareState.X
                    }
                }
                responseQ.add("Player=${remotePlayers[responseQ].toString()}")
                messageGameplayDisplayStatus()
            }
        }
        if (message.startsWith("p:", true)) {
            validRequest = true
            val indexString = message[2].toString()
            val gridIndex = Integer.valueOf(indexString)
            if (remotePlayers[responseQ] == currPlayer) {  // Only allow the allocated player
                val validMove = playSquare(gridIndex)
                if (validMove) {
                    pushStateToClients() // Make sure all clients know about the change.
                }
            } else {
                responseQ.add("s:${encodeState(grid, currPlayer, winner)}")
            }
            messageGameplayDisplayStatus()
        }
        if (message == "status:") {
            validRequest = true
            responseQ.add("s:${encodeState(grid, currPlayer, winner)}")
        }
        if (message == "shutdown" || message == "abandoned") {
            validRequest = true
            responseQ.add(message)
            if (remotePlayers.containsKey(responseQ)) {
                remotePlayers.remove(responseQ)
            }
            messageGameplayDisplayStatus()
        }

        if (!validRequest) {
            Log.d(TAG, "invalid request: [$message]")
        }
    }

    private fun handleClientMessage(message: String, responseQ: Queue<String>) {
        if (message.startsWith("s:", true)) {
            val remoteState = message.substringAfter("s:")
            autoStatusCountdown = autoStatusCount  // Reset the auto-request countdown.

            if (previousStateUpdate != remoteState) {
                Log.d(TAG, "REMOTE Game Server sent state change: [$remoteState]")

                previousStateUpdate = remoteState

                val stateVars = decodeState(remoteState)
                grid = stateVars.grid
                currPlayer = stateVars.currPlayer
                winner = stateVars.winner

                saveGameState()
                messageGameplayDisplayStatus()
            }
        }
        if (message.startsWith("Player=", true)) {
            val allocatedPlayer = SquareState.valueOf(message.substringAfter("Player="))
            clientPlayer = allocatedPlayer
            Log.d(TAG, "Remote Game Server allocated [$clientPlayer] to this client.")
        }
        if (message == "shutdown" || message == "abandoned") {
            responseQ.add(message)
            switchToPureLocalMode()
        }
    }

    private fun handleActivityMessage(message: String) {
        if (message == "reset:") {
            resetGame()
            messageGameplayDisplayStatus()
        }
        if (message == "status:") {
            messageGameplayDisplayStatus()
        }
        if (message.startsWith("p:", true)) {
            if (gameMode == GameMode.CLIENT) {
                socketClient?.messageFromGameServer(message)
            } else {
                val indexString = message[2].toString()
                val gridIndex = Integer.valueOf(indexString)

                // Only allow the server to play an unallocated player
                if (!remotePlayers.containsValue(currPlayer)) {
                    val validMove= playSquare(gridIndex)
                    if (validMove) {
                        messageGameplayDisplayStatus()
                        if (gameMode == GameMode.SERVER) {
                            pushStateToClients()
                        }
                    }
                }
            }
        }
        if (message == "UpdateSettings") {
            messageSettingsDisplayStatus()
        }
        if (message == "StartServer:") {
            if (gameMode != GameMode.SERVER) {
                switchToLocalServerMode()
            }
        }
        if (message == "StartLocal:") {
            if (gameMode != GameMode.LOCAL) {
                switchToPureLocalMode()
            }
        }
        if (message.startsWith("RemoteServer:")) {
            if (gameMode != GameMode.CLIENT) {
                val ip = message.substringAfter(":", "")
                if (ip != "") {
                    switchToRemoteServerMode(ip)
                }
            }
        }
        if (message == "StopGame") {
            stopGame()
        }
    }

    private fun pushStateToClients() {
        socketServer?.pushMessageToClients("s:${encodeState(grid, currPlayer, winner)}")
    }

    fun pauseApp() {
        // TODO - pause App mode while the App isn't visible?
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

    private fun stopGame() {
        Log.d(TAG, "The Game Server is shutting down ...")
        gameIsRunning.set(false)

        if (gameMode == GameMode.SERVER) {
            socketServer?.shutdownRequest()
        }

        if (gameMode == GameMode.CLIENT) {
            socketClient?.shutdownRequest()
        }

        singletonGameServer = null
    }

    /**
     * Saves the current App state.
     */
    private fun saveGameState() {
        // The winner isn't explicitly stored, because it can be derived from the game state.
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

        checkWinner() // Because the winner is only implied by the game state, not explicitly stored.
    }

    private fun messageSettingsDisplayStatus() {
        Log.d(TAG, "Sending the settings ...")

        val intent = Intent()
        intent.action = context.packageName + SettingsActivity.DISPLAY_MESSAGE_SUFFIX
        intent.putExtra("CurrMode", gameMode.toString())

        if (gameMode == GameMode.CLIENT) {
            intent.putExtra("RemoteServer", socketClient?.getServer())
        }
        if (gameMode == GameMode.SERVER) {
            intent.putExtra("ClientCount", socketServer?.countOfClients())
        }

        var listAsString = ""
        if (gameMode == GameMode.SERVER) {
            listAsString = ""
            allIpAddresses.forEach { addr ->
                if (listAsString.isNotEmpty()) {
                    listAsString += ", "
                }
                listAsString += addr
            }
        }
        intent.putExtra("IpAddressList", listAsString)

        context.sendBroadcast(intent)
    }

    private fun messageGameplayDisplayStatus() {
        val intent = Intent()
        intent.action = context.packageName + GameplayActivity.DISPLAY_MESSAGE_SUFFIX
        intent.putExtra("State", encodeState(grid, currPlayer, winner))

        var turnFlag = false;
        if (winner == SquareState.E) {
            if (gameMode == GameMode.LOCAL) {
                turnFlag = true
            }
            if (gameMode == GameMode.CLIENT && currPlayer == clientPlayer) {
                turnFlag = true
            }
            if (gameMode == GameMode.SERVER && !remotePlayers.containsValue(currPlayer)) {
                turnFlag = true
            }
        }
        intent.putExtra("YourTurn", turnFlag)

        context.sendBroadcast(intent)
    }

    private fun resetGame() {
        for (i in 0..8) {
            grid[i] = SquareState.E
        }
        winner = SquareState.E
        currPlayer = SquareState.X
        saveGameState()
        pushStateToClients()
    }

    private fun playSquare(gridIndex: Int): Boolean {
        if (winner != SquareState.E) {
            return false  // No more moves after a win
        }

        if (grid[gridIndex] != SquareState.E) {
            return  false  // Can only change Empty squares
        }

        grid[gridIndex] = currPlayer
        Log.d(TAG, "Play $gridIndex")

        checkWinner()
        if (winner == SquareState.E) {
            // No winner, so switch to next player
            currPlayer = if (currPlayer == SquareState.X) {
                SquareState.O
            } else {
                SquareState.X
            }
        }
        saveGameState()
        return true
    }

    private fun checkWinner() {
        val winSquares = getWinningIndices(grid)
        if (winSquares != null) {
            winner = grid[winSquares[0]]
        }
    }

    data class StateVariables(var grid: Array<SquareState>, val currPlayer: SquareState, val winner: SquareState, val winSquares: List<Int>?)

    companion object {
        private val TAG = GameServer::class.java.simpleName

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

        private fun getWinningIndices(grid: Array<SquareState>): List<Int>? {
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

        private fun encodeGrid(grid: Array<SquareState>): String {
            var encoded = ""
            for (i in 0..8) {
                encoded += grid[i].toString()
            }
            return encoded
        }

        fun encodeState(grid: Array<SquareState>, currPlayer: SquareState, winner: SquareState ): String {
            var state = "${encodeGrid(grid)}$currPlayer$winner"

            if (winner == SquareState.E) {
                state += "EEE"
            } else {
                getWinningIndices(grid)?.forEach { square ->
                    state += square.toString()
                }
            }

            return state
        }

        fun decodeState(stateString: String): StateVariables {
            Log.d(TAG, "decodeState() for $stateString")

            val grid: Array<SquareState> = Array(9) {SquareState.E}
            for (i in 0..8) {
                grid[i] = SquareState.valueOf(stateString[i].toString())
            }
            val currPlayer = SquareState.valueOf(stateString[9].toString())
            val winner = SquareState.valueOf(stateString[10].toString())

            var winSquares: List<Int>? = null
            if (stateString[11].isDigit()) {
                winSquares = listOf(stateString[11].digitToInt(), stateString[12].digitToInt(), stateString[13].digitToInt())
            }

            return StateVariables(grid, currPlayer, winner, winSquares)
        }

        // The GameServer always runs in it's own thread,
        // and stopGame() must be called as the App closes to avoid a memory leak.
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

        fun getGameMode(): GameMode? {
            return singletonGameServer?.getGameMode()
        }

        fun queueActivityMessage(message: String) {
            if (singletonGameServer?.gameIsRunning!!.get()) {
                singletonGameServer?.fromActivitiesToGameSeverQ?.add(message)
            }
        }

        fun queueClientHandlerMessage(message: String, responseQ: Queue<String>) {
            if (singletonGameServer?.gameIsRunning!!.get()) {
                singletonGameServer?.fromClientHandlerToGameServerQ?.add(ClientRequest(message, responseQ))
            }
        }

        fun queueClientMessage(message: String, responseQ: Queue<String>) {
            if (singletonGameServer?.gameIsRunning!!.get()) {
                singletonGameServer?.fromClientToGameServerQ?.add(ClientRequest(message, responseQ))
            }
        }
    }
}