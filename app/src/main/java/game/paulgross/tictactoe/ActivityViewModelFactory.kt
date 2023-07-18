package game.paulgross.tictactoe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

class ActivityViewModelFactory(): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ActivityViewModel::class.java)) {
            return ActivityViewModel() as T
        }
        throw IllegalArgumentException("Unknown View Model Class")
    }

    companion object {
        private val TAG = ActivityViewModelFactory::class.java.simpleName

        // Will start a new GameServer if required
        fun attachToLocalGameServer(owner: ViewModelStoreOwner, applicationContext: Context, sharedPreferences: SharedPreferences): GameServer {
            // Check to see if the link to the local GameServer is already stored in the ViewModel
            var viewModel =
                ViewModelProvider(owner, ActivityViewModelFactory()).get(ActivityViewModel::class.java)
            var localGameServer = viewModel.retrieveGameServerPointer()

            if (localGameServer == null) {
                Log.d(TAG, "Starting a new local GameServer ...")
                localGameServer =
                    GameServer(applicationContext, sharedPreferences)
                localGameServer?.start()
                viewModel.storeGameServerPointer(localGameServer!!)  // Store the link to the local GameServer
            } else {
                Log.d(TAG, "Reattached to the existing local GameServer.")
                localGameServer?.queueClientRequest("resume:")
            }

            return localGameServer
        }
    }
}


