package com.example.where2next.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.example.where2next.adapters.EventAdapter
import com.example.where2next.models.Event
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.firestore.FirebaseFirestore

class SearchResultsFragment : Fragment(R.layout.fragment_home) {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: EventAdapter
    private lateinit var shimmerCarousel: ShimmerFrameLayout
    private lateinit var shimmerDiscover: ShimmerFrameLayout
    private val searchResults = mutableListOf<Event>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val query = arguments?.getString("SEARCH_QUERY") ?: ""
        val lowerQuery = query.lowercase()

        // Bind shimmers
        shimmerCarousel = view.findViewById(R.id.shimmerCarousel)
        shimmerDiscover = view.findViewById(R.id.shimmerDiscover)

        // Hide carousel-specific UI that doesn't apply to search results
        view.findViewById<TextView>(R.id.titlePopular).text = "Search Results for '$query'"
        view.findViewById<TextView>(R.id.titleDiscover).visibility = View.GONE
        view.findViewById<View>(R.id.tabLayoutDots).visibility = View.GONE
        view.findViewById<View>(R.id.carouselPopularEvents).visibility = View.GONE
        view.findViewById<View>(R.id.carouselContainer).visibility = View.GONE
        view.findViewById<View>(R.id.cardSearch).visibility = View.GONE

        // Hide carousel shimmer immediately — we only need the discover/list shimmer
        shimmerCarousel.stopShimmer()
        shimmerCarousel.visibility = View.GONE

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.visibility = View.GONE // hide until data loads

        adapter = EventAdapter(searchResults) { selectedEvent ->
            val bundle = Bundle()
            bundle.putParcelable("SELECTED_EVENT", selectedEvent)
            val detailsFragment = EventDetailsFragment()
            detailsFragment.arguments = bundle
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, detailsFragment)
                .addToBackStack(null)
                .commit()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        performFullSearch(query, lowerQuery, view, recyclerView)
    }

    private fun performFullSearch(
        originalQuery: String,
        lowerQuery: String,
        view: View,
        recyclerView: RecyclerView
    ) {
        db.collection("events")
            .orderBy("title")
            .startAt(originalQuery)
            .endAt(originalQuery + "\uf8ff")
            .get()
            .addOnSuccessListener { titleSnapshot ->
                val cleanTag = lowerQuery.replace(" ", "_")

                db.collection("events")
                    .whereArrayContains("tags", cleanTag)
                    .get()
                    .addOnSuccessListener { tagSnapshot ->

                        val combined = (titleSnapshot.documents + tagSnapshot.documents)
                            .distinctBy { it.id }
                            .mapNotNull { doc ->
                                val event = doc.toObject(Event::class.java)
                                event?.apply { eventId = doc.id }
                            }

                        searchResults.clear()
                        searchResults.addAll(combined)
                        adapter.notifyDataSetChanged()

                        // Stop shimmer and show results
                        shimmerDiscover.stopShimmer()
                        shimmerDiscover.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE

                        if (searchResults.isEmpty()) {
                            view.findViewById<TextView>(R.id.titlePopular)?.text =
                                "No results found for '$originalQuery'"
                        }
                    }
                    .addOnFailureListener {
                        stopShimmerAndShow(recyclerView)
                    }
            }
            .addOnFailureListener {
                stopShimmerAndShow(recyclerView)
            }
    }

    private fun stopShimmerAndShow(recyclerView: RecyclerView) {
        shimmerCarousel.stopShimmer()
        shimmerCarousel.visibility = View.GONE
        shimmerDiscover.stopShimmer()
        shimmerDiscover.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}