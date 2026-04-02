package com.example.where2next.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.where2next.R
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenuButtons(view)
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