package com.example.where2next

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.where2next.databinding.ActivityForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Pre-fill email if passed from LoginActivity
        val passedEmail = intent.getStringExtra("PREFILL_EMAIL")
        if (!passedEmail.isNullOrEmpty()) {
            binding.editResetEmail.setText(passedEmail)
        }

        // Back button behavior
        binding.textBackToLogin.setOnClickListener {
            finish() // Simply closes this activity and returns to Login
        }

        // Send Link logic
        binding.btnSendResetLink.setOnClickListener {
            val email = binding.editResetEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button while processing to prevent double-clicks
            binding.btnSendResetLink.isEnabled = false
            binding.btnSendResetLink.text = "Sending..."

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Reset link sent! Please check your email inbox (and spam folder).", Toast.LENGTH_LONG).show()
                    finish() // Send them back to the login screen so they can log in once they change it
                }
                .addOnFailureListener { e ->
                    binding.btnSendResetLink.isEnabled = true
                    binding.btnSendResetLink.text = "Send Reset Link"
                    Toast.makeText(this, "Failed to send link: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}