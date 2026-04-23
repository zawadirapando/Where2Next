package com.example.where2next.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.where2next.R
import com.example.where2next.adapters.CarouselAdapter
import com.example.where2next.adapters.EventAdapter
import com.example.where2next.models.Event
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private val auth = FirebaseAuth.getInstance()

    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    private lateinit var shimmerCarousel: ShimmerFrameLayout
    private lateinit var shimmerDiscover: ShimmerFrameLayout
    private lateinit var carouselViewPager: ViewPager2
    private lateinit var tabLayoutDots: TabLayout
    private lateinit var standardRecyclerView: RecyclerView
    private lateinit var textLocation: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        db = FirebaseFirestore.getInstance()

        shimmerCarousel = view.findViewById(R.id.shimmerCarousel)
        shimmerDiscover = view.findViewById(R.id.shimmerDiscover)
        carouselViewPager = view.findViewById(R.id.carouselPopularEvents)
        tabLayoutDots = view.findViewById(R.id.tabLayoutDots)
        standardRecyclerView = view.findViewById(R.id.recyclerView)
        textLocation = view.findViewById(R.id.textCurrentLocation)

        standardRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        // ── Show cached location instantly, then refresh from Firestore in background ──
        loadLocationInstantly()

        fetchUserInterestsThenEvents(view)

        val iconDropdown = view.findViewById<ImageView>(R.id.iconDropdown)
        val cardSearch = view.findViewById<View>(R.id.cardSearch)

        cardSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, EventSearchFragment())
                .addToBackStack(null)
                .commit()
        }

        val clickListener = View.OnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, SearchLocationFragment())
                .addToBackStack(null)
                .commit()
        }

        textLocation.setOnClickListener(clickListener)
        iconDropdown.setOnClickListener(clickListener)

        return view
    }

    private fun loadLocationInstantly() {
        // Step 1 — show cached location immediately (no loading flash)
        val prefs = requireContext().getSharedPreferences("Where2NextPrefs", Context.MODE_PRIVATE)
        val cachedLocation = prefs.getString("cached_location", null)

        if (!cachedLocation.isNullOrEmpty()) {
            textLocation.text = cachedLocation
        } else {
            textLocation.text = "Set Location"
        }

        // Step 2 — fetch fresh from Firestore in background and update cache
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val freshLocation = document.getString("location")
                    if (!freshLocation.isNullOrEmpty()) {
                        // Update UI
                        textLocation.text = freshLocation
                        // Update cache so next launch is instant
                        prefs.edit().putString("cached_location", freshLocation).apply()
                    } else {
                        textLocation.text = "Set Location"
                    }
                }
            }
    }

    private fun fetchUserInterestsThenEvents(view: View) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            fetchAndDisplayEvents(emptyList(), null)
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val rawInterests = document.get("tags") as? List<*> ?: emptyList<Any>()
                val interestTags = rawInterests.map { interest ->
                    interest.toString().lowercase().replace(" ", "_")
                }
                fetchAndDisplayEvents(interestTags, userId)
            }
            .addOnFailureListener {
                fetchAndDisplayEvents(emptyList(), userId)
            }
    }

    private fun fetchAndDisplayEvents(userInterestTags: List<String>, currentUserId: String?) {
        db.collection("events")
            .get()
            .addOnSuccessListener { documents ->
                val allEvents = mutableListOf<Event>()

                for (document in documents) {
                    val event = document.toObject(Event::class.java)
                    event.eventId = document.id
                    allEvents.add(event)
                }

                // ── Filter out events the current user created ──
                val eventsNotByUser = if (currentUserId != null) {
                    allEvents.filter { it.creatorId != currentUserId }
                } else {
                    allEvents
                }

                // Carousel — top 6 by capacity, excluding user's own events
                val carouselEvents = eventsNotByUser
                    .sortedByDescending { it.totalCapacity }
                    .take(6)

                // Discover — filtered by interests, excluding user's own events
                val discoverEvents = if (userInterestTags.isEmpty()) {
                    eventsNotByUser.sortedByDescending { it.dateAndTime }
                } else {
                    eventsNotByUser.filter { event ->
                        event.tags.any { tag -> userInterestTags.contains(tag) }
                    }.sortedByDescending { it.dateAndTime }
                }

                shimmerCarousel.stopShimmer()
                shimmerCarousel.visibility = View.GONE
                shimmerDiscover.stopShimmer()
                shimmerDiscover.visibility = View.GONE

                carouselViewPager.visibility = View.VISIBLE
                tabLayoutDots.visibility = View.VISIBLE
                standardRecyclerView.visibility = View.VISIBLE

                carouselViewPager.adapter = CarouselAdapter(carouselEvents) { clickedEvent ->
                    navigateToEventDetails(clickedEvent)
                }

                standardRecyclerView.adapter = EventAdapter(discoverEvents) { clickedEvent ->
                    navigateToEventDetails(clickedEvent)
                }

                view?.findViewById<TextView>(R.id.titleDiscover)?.text = if (userInterestTags.isEmpty()) {
                    "Discover"
                } else {
                    "Based on your interests"
                }

                TabLayoutMediator(tabLayoutDots, carouselViewPager) { _, _ -> }.attach()
                startAutoScroll(carouselViewPager)
            }
            .addOnFailureListener { exception ->
                Log.e("HomeFragment", "Error fetching events: ", exception)
                Toast.makeText(requireContext(), "Failed to load events", Toast.LENGTH_SHORT).show()

                shimmerCarousel.stopShimmer()
                shimmerCarousel.visibility = View.GONE
                shimmerDiscover.stopShimmer()
                shimmerDiscover.visibility = View.GONE
            }
    }

    private fun startAutoScroll(carousel: ViewPager2) {
        scrollRunnable = Runnable {
            val adapter = carousel.adapter
            if (adapter != null && adapter.itemCount > 0) {
                val nextSlide = (carousel.currentItem + 1) % adapter.itemCount
                carousel.setCurrentItem(nextSlide, true)
                scrollHandler.postDelayed(scrollRunnable!!, 5000)
            }
        }
        scrollHandler.postDelayed(scrollRunnable!!, 5000)
    }

    private fun navigateToEventDetails(clickedEvent: Event) {
        val detailsFragment = EventDetailsFragment()
        val bundle = Bundle()
        bundle.putParcelable("SELECTED_EVENT", clickedEvent)
        detailsFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, detailsFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
    }
}