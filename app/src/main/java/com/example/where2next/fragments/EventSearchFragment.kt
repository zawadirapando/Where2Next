package com.example.where2next.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

data class EventSearchItem(
    val suggestion: String,
    val isRecent: Boolean
)

class EventSearchFragment : Fragment(R.layout.fragment_event_search) {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: SearchSuggestionAdapter
    private lateinit var textListLabel: TextView
    private lateinit var recyclerView: RecyclerView

    private var currentList = mutableListOf<EventSearchItem>()
    private val PREFS_NAME = "Where2NextEventSearchPrefs"
    private val HISTORY_KEY = "recent_event_searches"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editSearchEvent = view.findViewById<TextInputEditText>(R.id.editSearchEvent)
        val buttonClose = view.findViewById<ImageButton>(R.id.buttonClose)
        textListLabel = view.findViewById(R.id.textListLabel)
        recyclerView = view.findViewById(R.id.recyclerViewSearch)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchSuggestionAdapter(currentList) { selectedTerm ->
            handleSearchSelected(selectedTerm, view)
        }
        recyclerView.adapter = adapter

        buttonClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadRecentSearches()

        editSearchEvent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                if (query.isNotEmpty()) {
                    textListLabel.text = "Results"
                    fetchSuggestions(query)
                } else {
                    textListLabel.text = "Recent"
                    loadRecentSearches()
                }
            }
        })
    }

    private fun fetchSuggestions(query: String) {
        // Clean the query identical to how we built the tokens in CreateFragment
        val cleanQuery = query.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9 ]"), "").trim()

        if (cleanQuery.isEmpty()) return

        Log.d("SearchDebug", "Searching for token: $cleanQuery")

        // Look inside the array of search tokens!
        db.collection("events")
            .whereArrayContains("searchTokens", cleanQuery)
            .limit(8)
            .get()
            .addOnSuccessListener { snapshot ->
                currentList.clear()

                if (!snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val title = doc.getString("title") ?: "Unknown Event"
                        currentList.add(EventSearchItem(title, isRecent = false))
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("SearchDebug", "Error searching", e)
            }
    }

    private fun handleSearchSelected(searchTerm: String, view: View) {
        if (searchTerm.startsWith("No results found")) {
            return
        }
        val finalQuery = searchTerm.replace("Search tags/topics for: ", "").trim()

        saveToRecentSearches(finalQuery)

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        val bundle = Bundle()
        bundle.putString("SEARCH_QUERY", finalQuery)

        val resultsFragment = SearchResultsFragment()
        resultsFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, resultsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadRecentSearches() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, "") ?: ""

        currentList.clear()
        if (historyString.isNotEmpty()) {
            val historyArray = historyString.split("||")
            for (term in historyArray) {
                currentList.add(EventSearchItem(term, isRecent = true))
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveToRecentSearches(searchTerm: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, "") ?: ""

        val historyList = if (historyString.isNotEmpty()) historyString.split("||").toMutableList() else mutableListOf()
        historyList.remove(searchTerm)
        historyList.add(0, searchTerm)

        if (historyList.size > 5) {
            historyList.removeAt(historyList.size - 1)
        }

        val newHistoryString = historyList.joinToString("||")
        prefs.edit().putString(HISTORY_KEY, newHistoryString).apply()
    }

    inner class SearchSuggestionAdapter(
        private val items: List<EventSearchItem>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<SearchSuggestionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.textCityName)
            val iconImage: ImageView = view.findViewById(R.id.iconLocation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location_prediction, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textName.text = item.suggestion

            if (item.isRecent) {
                holder.iconImage.setImageResource(R.drawable.ic_recent)
            } else {
                holder.iconImage.setImageResource(R.drawable.ic_search)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item.suggestion)
            }
        }

        override fun getItemCount() = items.size
    }
}   