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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class InterestsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_interests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupInterests)
        val saveButton = view.findViewById<Button>(R.id.buttonSaveInterests)

        saveButton.setOnClickListener {
            val selectedInterests = mutableListOf<String>()

            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedInterests.add(chip.text.toString())
                }
            }

            // TODO: Save 'selectedInterests' list to Firebase under the user's document
            Toast.makeText(context, "Interests Saved!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }
}