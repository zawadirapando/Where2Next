package com.example.where2next.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OnboardingInterestsFragment : Fragment(R.layout.fragment_onboarding_interests) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = listOf("Live Music", "Tech", "Food", "Art", "Sports", "Networking", "Nightlife", "Workshops")

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupOnboarding)
        val btnContinue = view.findViewById<Button>(R.id.buttonContinueInterests)

        for (category in categories) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                // You can apply your styles here
                setChipBackgroundColorResource(R.color.chip_background_state)
            }
            chipGroup.addView(chip)
        }

        btnContinue.setOnClickListener {
            val selectedInterests = mutableListOf<String>()
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedInterests.add(chip.text.toString())
                }
            }

            if (selectedInterests.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one interest", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid
            if (userId != null) {
                db.collection("users").document(userId)
                    .update("interests", selectedInterests)
                    .addOnSuccessListener {
                        // Navigate to Step 3: Notifications
                        parentFragmentManager.beginTransaction()
                            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                            .replace(R.id.onboarding_fragment_container, OnboardingNotificationsFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to save interests", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}