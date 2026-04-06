package com.example.where2next.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.where2next.LoginActivity
import com.example.where2next.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadUserProfile(view)
        setupClickListeners(view)
        setupMenuButtons(view)
    }

    private fun loadUserProfile(view: View) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        val textName = view.findViewById<TextView>(R.id.textProfileName)
        val textLocation = view.findViewById<TextView>(R.id.textProfileLocation)
        val imageProfile = view.findViewById<ImageView>(R.id.imageProfile)
        val textInitials = view.findViewById<TextView>(R.id.textAvatarInitials)

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""

                    val fullName = "$firstName $lastName".trim()

                    val name = if (fullName.isNotEmpty()) fullName else "Unknown User"
                    val location = document.getString("location") ?: "Location not set"
                    val profileImageUrl = document.getString("profileImageUrl")

                    textName.text = name
                    textLocation.text = location

                    // setting initials
                    val initials = name.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    textInitials.text = initials

                    // image
                    if (!profileImageUrl.isNullOrEmpty()) {
                        imageProfile.visibility = View.VISIBLE
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(imageProfile)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.buttonMyEvents).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout,  MyEventsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.buttonMyInterests).setOnClickListener {
            Toast.makeText(context, "Opening Interests...", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.buttonPayoutMethods).setOnClickListener {
            Toast.makeText(context, "Opening Payout Methods...", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.buttonHelpCenter).setOnClickListener {
            // Open email
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:zawadirapando@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Help with Where2Next App")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        }

        view.findViewById<View>(R.id.buttonLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun setupMenuButtons(view: View){
        view.findViewById<MaterialButton>(R.id.buttonEditProfile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout,  EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }
    }

}