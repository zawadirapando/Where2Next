package com.example.where2next.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PayoutMethodsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_payout_methods, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val mpesaInput = view.findViewById<TextInputEditText>(R.id.inputMpesaNumber)
        val saveButton = view.findViewById<Button>(R.id.buttonSavePayout)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get().addOnSuccessListener { document ->
                val existingNumber = document.getString("mpesaNumber")
                if (!existingNumber.isNullOrEmpty()) {
                    mpesaInput.setText(existingNumber)
                }
            }
        }

        saveButton.setOnClickListener {
            val phoneNumber = mpesaInput.text.toString().trim()

            if (phoneNumber.isEmpty() || phoneNumber.length < 10) {
                mpesaInput.error = "Please enter a valid M-Pesa number"
                return@setOnClickListener
            }

            if (userId != null) {
                saveButton.isEnabled = false
                saveButton.text = "Saving..."

                db.collection("users").document(userId)
                    .update("mpesaNumber", phoneNumber)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Payout method updated successfully", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    .addOnFailureListener {
                        saveButton.isEnabled = true
                        saveButton.text = "Save Payout Method"
                        Toast.makeText(context, "Failed to update payout method", Toast.LENGTH_SHORT).show()
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