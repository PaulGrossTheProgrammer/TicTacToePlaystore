package game.paulgross.tictactoe

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View

class SettingsActivity : AppCompatActivity() {

    private var localGameServer: GameServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Log.d(TAG, "Getting GameServer ...")
        localGameServer = GameServer.getSingleton(applicationContext, getPreferences(MODE_PRIVATE))
    }

    fun onClickBack(view: View) {
        val intent: Intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    companion object {
        private val TAG = SettingsActivity::class.java.simpleName
    }
}


