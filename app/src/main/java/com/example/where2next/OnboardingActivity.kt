package com.example.where2next

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.where2next.fragments.OnboardingLocationFragment
import com.google.android.libraries.places.api.Places

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        // Initialize Places API once for the onboarding flow
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        // Load the first step: Location
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboarding_fragment_container, OnboardingLocationFragment())
                .commit()
        }
    }
}