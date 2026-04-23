package com.example.where2next.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    // Handles the location permission result — we proceed either way
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permission granted or denied — either way the user can still
        // type their city manually, so we just let them continue
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ask for location permission as soon as this screen appears
        checkAndRequestLocationPermission()

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



    private fun checkAndRequestLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) return // already have it, nothing to do

        // ✅ If user previously denied, show a rationale before re-asking
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Location Access")
                .setMessage("Allowing location access lets us show you events happening near you.")
                .setPositiveButton("Allow") { _, _ ->
                    requestLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                .setNegativeButton("Skip") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            // ✅ First time asking — launch directly
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun handleCitySelected(cityName: String, view: View) {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .update("location", cityName)
                .addOnSuccessListener {
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