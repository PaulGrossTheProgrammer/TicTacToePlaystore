package game.paulgross.tictactoe

import androidx.lifecycle.ViewModel

class ActivityViewModel(): ViewModel() {
    private var gameServer: GameServer? = null
    fun retrieveGameServerPointer(): GameServer? {
        return gameServer
    }

    fun storeGameServerPointer(theServer: GameServer) {
        gameServer = theServer
    }

    fun clearGameServerPointer() {
        gameServer = null
    }
}