package com.example.where2next

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.where2next.databinding.ActivityNavigationBinding
import com.example.where2next.fragments.CreateFragment
import com.example.where2next.fragments.HomeFragment
import com.example.where2next.fragments.ProfileFragment
import com.example.where2next.fragments.TicketsFragment
import com.google.firebase.auth.FirebaseAuth

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("Where2NextPrefs", MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)

        // 1. Auth Gate
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 2. Onboarding Gate
        if (!hasCompletedOnboarding) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // 3. Setup UI (Only happens if gates 1 & 2 are passed)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupKeyboardListener()

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home -> replaceFragment(HomeFragment())
                R.id.create -> replaceFragment(CreateFragment())
                R.id.tickets -> replaceFragment(TicketsFragment())
                R.id.profile -> replaceFragment(ProfileFragment())
            }
            true
        }
    }

    private fun setupKeyboardListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keyboardHeight = screenHeight - r.bottom

            if(keyboardHeight > screenHeight * 0.15) {
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                // Check if we are on a top-level fragment before showing nav again
                val currentFrag = supportFragmentManager.findFragmentById(R.id.frameLayout)
                if(currentFrag is HomeFragment || currentFrag is CreateFragment ||
                    currentFrag is TicketsFragment || currentFrag is ProfileFragment) {
                    binding.bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }
}