package com.example.where2next.fragments

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.where2next.R
import com.example.where2next.adapters.CarouselAdapter
import com.example.where2next.adapters.EventAdapter
import com.example.where2next.models.Event
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var db : FirebaseFirestore
    private val auth = FirebaseAuth.getInstance()

    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view =  inflater.inflate(R.layout.fragment_home, container, false)

        db = FirebaseFirestore.getInstance()

        val carouselViewPager = view.findViewById<ViewPager2>(R.id.carouselPopularEvents)
        val tabLayoutDots = view.findViewById<TabLayout>(R.id.tabLayoutDots)
        val standardRecyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        standardRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        fetchEventsFromFirebase(carouselViewPager, tabLayoutDots, standardRecyclerView)

        fetchUserLocation(view)
         val textLocation = view.findViewById<TextView>(R.id.textCurrentLocation)
        val iconDropdown = view.findViewById<ImageView>(R.id.iconDropdown)

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

    private fun fetchUserLocation(view: View) {
        val userId = auth.currentUser?.uid ?: return
        val locationText = view.findViewById<TextView>(R.id.textCurrentLocation)

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document->
                if (document != null && document.exists()){
                    val savedLocation = document.getString("location")
                    if (!savedLocation.isNullOrEmpty()){
                        locationText.text = savedLocation
                    }else {
                        locationText.text = "Set Location"
                    }
                }
            }
    }


    private fun fetchEventsFromFirebase (carousel : ViewPager2, tabLayout: TabLayout, standardList : RecyclerView) {
        db.collection("events")
            .get()
            .addOnSuccessListener() { documents ->
                val liveDatabase = mutableListOf<Event>()

                for (document in documents) {
                    val event = document.toObject(Event::class.java)

                    event.eventId = document.id
                    
                    liveDatabase.add(event)
                }

                carousel.adapter = CarouselAdapter(liveDatabase) { clickedEvent ->
                    navigateToEventDetails(clickedEvent)
                }
                standardList.adapter = EventAdapter(liveDatabase) { clickedEvent ->
                    navigateToEventDetails(clickedEvent)
                }

                TabLayoutMediator(tabLayout, carousel) { tab, position ->

                }.attach()

                startAutoScroll(carousel)
            }
            .addOnFailureListener(){ exception ->
                Log.e("Home Fragment", "Error downloading events: ", exception)
                Toast.makeText(requireContext(), "Failed to load events", Toast.LENGTH_SHORT).show()
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

    private fun navigateToEventDetails(clickedEvent : Event){
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