package com.ancientpersia.rps

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.ancientpersia.rps.audio.SoundManager
import com.ancientpersia.rps.data.GamePrefs
import com.ancientpersia.rps.databinding.ActivityMainBinding
import com.ancientpersia.rps.ui.GameFragment
import com.ancientpersia.rps.ui.MenuFragment
import com.ancientpersia.rps.ui.ShopFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GamePrefs.init(this)
        SoundManager.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = supportFragmentManager.findFragmentById(R.id.container)
                if (current is GameFragment || current is ShopFragment) {
                    showMenu()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState == null) {
            showMenu()
        }
    }

    fun showMenu() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, MenuFragment())
            .commit()
    }

    fun showGame() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, GameFragment())
            .commit()
    }

    fun showShop() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ShopFragment())
            .commit()
    }

    override fun onDestroy() {
        SoundManager.release()
        super.onDestroy()
    }
}
