package game.paulgross.tictactoe

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider

class SettingsActivity : AppCompatActivity() {

    private var localGameServer: GameServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        localGameServer = ActivityViewModelFactory.attachToLocalGameServer(this, applicationContext, getPreferences(MODE_PRIVATE))
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
    }
}