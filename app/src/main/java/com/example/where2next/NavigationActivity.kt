package com.example.where2next

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.where2next.databinding.ActivityNavigationBinding
import com.example.where2next.fragments.CreateFragment
import com.example.where2next.fragments.EventDetailsFragment
import com.example.where2next.fragments.HomeFragment
import com.example.where2next.fragments.ProfileFragment
import com.example.where2next.fragments.TicketsFragment
import com.example.where2next.models.Event
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class NavigationActivity : AppCompatActivity() {

    private var isReady = false
    private lateinit var binding: ActivityNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.brand_magenta)
        splashScreen.setKeepOnScreenCondition { !isReady }

        performStartupLogic()

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("Where2NextPrefs", MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (!hasCompletedOnboarding) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateFcmToken(auth.currentUser?.uid)
        setupKeyboardListener()

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> replaceFragment(HomeFragment())
                R.id.create -> replaceFragment(CreateFragment())
                R.id.tickets -> replaceFragment(TicketsFragment())
                R.id.profile -> replaceFragment(ProfileFragment())
            }
            true
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "EVENT_CHANNEL_ID",
                "Event Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Handle notification tap after everything is set up
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val eventId = intent?.getStringExtra("OPEN_EVENT_ID") ?: return

        val db = FirebaseFirestore.getInstance()
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) return@addOnSuccessListener

                val event = document.toObject(Event::class.java) ?: return@addOnSuccessListener

                val fragment = EventDetailsFragment()
                fragment.arguments = Bundle().apply {
                    putParcelable("SELECTED_EVENT", event)
                }

                supportFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, fragment)
                    .addToBackStack(null)
                    .commit()
            }
            .addOnFailureListener { e ->
                Log.e("NavigationActivity", "Failed to load event from notification: ${e.message}")
            }
    }

    private fun performStartupLogic() {
        Handler(Looper.getMainLooper()).postDelayed({
            isReady = true
        }, 3000)
    }

    private fun setupKeyboardListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keyboardHeight = screenHeight - r.bottom

            if (keyboardHeight > screenHeight * 0.15) {
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                val currentFrag = supportFragmentManager.findFragmentById(R.id.frameLayout)
                if (currentFrag is HomeFragment || currentFrag is CreateFragment ||
                    currentFrag is TicketsFragment || currentFrag is ProfileFragment
                ) {
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

    private fun updateFcmToken(userId: String?) {
        if (userId == null) return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseFirestore.getInstance().collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnFailureListener { e ->
                        Log.e("FCM", "Failed to update token: ${e.message}")
                    }
            } else {
                Log.e("FCM", "Fetching FCM token failed", task.exception)
            }
        }
    }
}