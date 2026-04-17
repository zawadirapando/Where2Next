package com.example.where2next.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.where2next.NavigationActivity
import com.example.where2next.R

class OnboardingNotificationsFragment : Fragment(R.layout.fragment_onboarding_notifications) {

    // Handles the permission request callback
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        finishOnboarding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.buttonEnableNotifications).setOnClickListener {
            askNotificationPermission()
        }

        view.findViewById<Button>(R.id.buttonSkipNotifications).setOnClickListener {
            finishOnboarding()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Already granted
                finishOnboarding()
            } else {
                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Permission not required for Android 12 and below
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // Mark onboarding as complete in SharedPreferences so we don't show it again
        val prefs = requireContext().getSharedPreferences("Where2NextPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()

        // Navigate to MainActivity and clear the backstack
        val intent = Intent(requireContext(), NavigationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}