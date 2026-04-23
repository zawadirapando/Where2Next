package com.example.where2next.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class InterestsFragment : Fragment(R.layout.fragment_interests) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = listOf(
            "Live Music", "Nightlife", "Food And Drink", "Arts And Culture",
            "Sports", "Wellness", "Comedy", "Networking", "Markets",
            "Outdoor", "Film", "Family", "Fashion", "Gaming"
        )

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupInterests)
        val btnSave = view.findViewById<Button>(R.id.buttonSaveInterests)
        val backToProfileButton = view.findViewById<ImageButton>(R.id.buttonBack)

        backToProfileButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Build all chips first (unchecked)
        for (category in categories) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_background_state)
            }
            chipGroup.addView(chip)
        }

        // ✅ FIX: Fetch saved interests and pre-check the matching chips
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    @Suppress("UNCHECKED_CAST")
                    val savedTags = document.get("tags") as? List<String> ?: emptyList()

                    for (i in 0 until chipGroup.childCount) {
                        val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                        val topicName = chip.text.toString().lowercase().replace(" ", "_")
                        chip.isChecked = savedTags.contains(topicName)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to load interests", Toast.LENGTH_SHORT).show()
                }
        }

        btnSave.setOnClickListener {
            val selectedInterests = mutableListOf<String>()

            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                val topicName = chip.text.toString().lowercase().replace(" ", "_")

                if (chip.isChecked) {
                    FirebaseMessaging.getInstance().subscribeToTopic(topicName)
                    selectedInterests.add(topicName)
                } else {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topicName)
                }
            }

            if (selectedInterests.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one interest", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userId != null) {
                db.collection("users").document(userId)
                    .update("tags", selectedInterests)
                    .addOnSuccessListener {
                        // ✅ FIX: Success toast before navigating back
                        Toast.makeText(requireContext(), "Interests updated!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.frameLayout, ProfileFragment())
                            .commit()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to save interests", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
    }
}