package com.example.where2next.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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

data class SearchItem(
    val cityName : String,
    val isRecent : Boolean
)

class SearchLocationFragment : Fragment(R.layout.fragment_search_location) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var placesClient: PlacesClient
    private lateinit var adapter: LocationPredictionAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var textListLabel: TextView

    private var currentList = mutableListOf<SearchItem>()
    private val PREFS_NAME = "Where2NextPrefs"
    private val HISTORY_KEY = "recent_locations"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(!Places.isInitialized()){
            Places.initialize(requireContext(), com.example.where2next.BuildConfig.MAPS_API_KEY)
        }

        placesClient = Places.createClient(requireContext())

        val editSearchLocation = view.findViewById<TextInputEditText>(R.id.editSearchLocation)
        val buttonClose = view.findViewById<ImageButton>(R.id.buttonClose)
        textListLabel = view.findViewById(R.id.textListLabel)
        recyclerView = view.findViewById(R.id.recyclerViewSearch)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = LocationPredictionAdapter(currentList) { selectedCity ->
            handleCitySelected(selectedCity, view)
        }
        recyclerView.adapter = adapter

        buttonClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadRecentSearches()

        editSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()

                if(query.length > 0){
                    textListLabel.text = "Results"

                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response->
                            currentList.clear()

                            for (prediction in response.autocompletePredictions) {
                                val cityName = prediction.getPrimaryText(null).toString()
                                currentList.add(SearchItem(cityName, isRecent = false))
                            }

                            adapter.notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                        }
                }else{
                    textListLabel.text = "Recent"
                    loadRecentSearches()
                }
            }
        })
    }

    private fun handleCitySelected(cityName: String, view: View){

        saveToRecentSearches(cityName)

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        val userId = auth.currentUser?.uid
        if (userId!=null){
            db.collection("users").document(userId)
                .update("location", cityName)
                .addOnSuccessListener {
                    parentFragmentManager.popBackStack()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to update location", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun loadRecentSearches() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, "") ?: ""

        currentList.clear()
        if (historyString.isNotEmpty()) {
            val historyArray = historyString.split("||")
            for (city in historyArray) {
                currentList.add(SearchItem(city, isRecent = true))
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveToRecentSearches(cityName: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, "") ?: ""

        val historyList = if (historyString.isNotEmpty()) historyString.split("||").toMutableList() else mutableListOf()
        historyList.remove(cityName)
        historyList.add(0, cityName)

        if (historyList.size > 5) {
            historyList.removeAt(historyList.size - 1)
        }

        val newHistoryString = historyList.joinToString("||")
        prefs.edit().putString(HISTORY_KEY, newHistoryString).apply()
    }


    inner class LocationPredictionAdapter(
        private val predictions : List<SearchItem>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<LocationPredictionAdapter.PredictionViewHolder>() {

        inner class PredictionViewHolder(view: View) : RecyclerView.ViewHolder(view){
            val textCityName = view.findViewById<TextView>(R.id.textCityName)
            val iconImage = view.findViewById<ImageView>(R.id.iconLocation)
        }

        override fun getItemCount(): Int = predictions.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location_prediction, parent, false)
            return PredictionViewHolder(view)
        }

        override fun onBindViewHolder(holder: PredictionViewHolder, position: Int) {
            val item = predictions[position]
            holder.textCityName.text = item.cityName


            if (item.isRecent) {
                holder.iconImage.setImageResource(R.drawable.ic_home)
            } else {
                holder.iconImage.setImageResource(R.drawable.ic_home)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item.cityName)
            }
        }
    }
}