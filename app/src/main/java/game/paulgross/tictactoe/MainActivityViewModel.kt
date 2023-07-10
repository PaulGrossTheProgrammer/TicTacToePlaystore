package game.paulgross.tictactoe

import androidx.lifecycle.ViewModel

class MainActivityViewModel(ignoreThis : Int): ViewModel() {

    private var gameServer: GameServer? = null
    fun getGameServer(): GameServer? {
        return gameServer
    }

    fun setGameServer(theServer: GameServer) {
        gameServer = theServer
    }
}