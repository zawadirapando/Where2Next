package com.example.where2next.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OnboardingLocationFragment : Fragment(R.layout.fragment_onboarding_location) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var placesClient: PlacesClient
    private lateinit var adapter: LocationAdapter
    private var currentList = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        placesClient = Places.createClient(requireContext())
        val editSearchLocation = view.findViewById<TextInputEditText>(R.id.editOnboardingLocation)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerOnboardingLocation)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = LocationAdapter(currentList) { selectedCity ->
            handleCitySelected(selectedCity, view)
        }
        recyclerView.adapter = adapter

        editSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            currentList.clear()
                            for (prediction in response.autocompletePredictions) {
                                currentList.add(prediction.getPrimaryText(null).toString())
                            }
                            adapter.notifyDataSetChanged()
                        }
                } else {
                    currentList.clear()
                    adapter.notifyDataSetChanged()
                }
            }
        })
    }

    private fun handleCitySelected(cityName: String, view: View) {
        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .update("location", cityName)
                .addOnSuccessListener {
                    // Navigate to Step 2: Interests
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .replace(R.id.onboarding_fragment_container, OnboardingInterestsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to save location", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Simple Adapter for Onboarding
    inner class LocationAdapter(
        private val cities: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<LocationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textCity: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val city = cities[position]
            holder.textCity.text = city
            holder.itemView.setOnClickListener { onItemClick(city) }
        }

        override fun getItemCount(): Int = cities.size
    }
}