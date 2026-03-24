package com.example.where2next

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.where2next.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.textSignUp.setOnClickListener{
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnLogIn.setOnClickListener{
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) {task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, NavigationActivity::class.java)
                        startActivity(intent)
                        finish()
                    }else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }

                }
        }
    }
}