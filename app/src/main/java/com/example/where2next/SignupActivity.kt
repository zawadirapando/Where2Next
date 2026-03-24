package com.example.where2next

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.where2next.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySignupBinding

    private lateinit var auth : FirebaseAuth
    private lateinit var db : FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.textLogIn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnSignUp.setOnClickListener {
            val firstName = binding.editFName.text.toString().trim()
            val lastName = binding.editLname.text.toString().trim()
            val email = binding.editEmail.text.toString().trim()
            val phone = binding.editPhone.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()){
                Toast.makeText(this, "Please fill in all the fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 8){
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful){
                        val userId = auth.currentUser?.uid

                        val userProfile = hashMapOf(
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "email" to email,
                            "phone" to phone,
                            "interestedTags" to listOf<String>()
                        )

                        if (userId != null){
                            db.collection("users").document(userId)
                                .set(userProfile)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()

                                    val intent = Intent(this, NavigationActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener() {e ->
                                    Toast.makeText(this, "Failed to save profile data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }else{
                        Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }


    }
}