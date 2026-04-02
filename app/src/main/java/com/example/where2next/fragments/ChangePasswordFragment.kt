package com.example.where2next.fragments

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {
    private lateinit var editCurrent : TextInputEditText
    private lateinit var editNew : TextInputEditText
    private lateinit var editConfirm : TextInputEditText
    private lateinit var buttonUpdate : MaterialButton

    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editCurrent = view.findViewById(R.id.editCurrentPassword)
        editNew = view.findViewById(R.id.editNewPassword)
        editConfirm = view.findViewById(R.id.editConfirmNewPassword)
        buttonUpdate = view.findViewById(R.id.buttonUpdatePassword)

        view.findViewById<ImageButton>(R.id.buttonBackToEdit).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        buttonUpdate.setOnClickListener {
            validateAndUpdatePassword()
        }
    }

    private fun validateAndUpdatePassword() {
        val currentPass = editCurrent.text.toString()
        val newPass = editNew.text.toString()
        val confirmPass = editConfirm.text.toString()

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()){
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass != confirmPass){
            Toast.makeText(requireContext(), "New passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass.length < 8) {
            Toast.makeText(requireContext(), "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser

        if (user != null && user.email != null) {
            buttonUpdate.isEnabled = false
            buttonUpdate.text = "Verifying..."

             val credential = EmailAuthProvider.getCredential(user.email!!, currentPass)

            user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    buttonUpdate.text = "Updating..."

                    user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        } else{
                            resetButton("Update failed: ${updateTask.exception?.message}")
                        }
                    }
                } else {
                    resetButton("Incorrect current password")
                }
            }
        } else{
            Toast.makeText(requireContext(), "Session error. Please log out and back in.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetButton(errorMessage: String) {
        buttonUpdate.isEnabled = true
        buttonUpdate.text = "Update Password"
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
    }
}
