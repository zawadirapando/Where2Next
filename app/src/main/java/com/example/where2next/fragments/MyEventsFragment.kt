package com.example.where2next.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.example.where2next.adapters.MyEventsAdapter
import com.example.where2next.models.Event
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyEventsFragment : Fragment() {

    private lateinit var adapter: MyEventsAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backToProfileButton = view.findViewById<ImageButton>(R.id.buttonBack)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerMyEvents)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressMyEvents)
        val textEmpty = view.findViewById<TextView>(R.id.textEmptyEvents)

        backToProfileButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        adapter = MyEventsAdapter(emptyList()) { selectedEvent ->
            val detailsFragment = EventDetailsFragment()
            val bundle = Bundle().apply { putParcelable("SELECTED_EVENT", selectedEvent) }
            detailsFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, detailsFragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = adapter

        fetchMyEvents(progressBar, textEmpty)
    }

    private fun fetchMyEvents(progressBar: ProgressBar, textEmpty: TextView) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("events")
            .whereEqualTo("creatorId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    textEmpty.visibility = View.VISIBLE
                } else {
                    textEmpty.visibility = View.GONE
                    val eventsList = documents.toObjects(Event::class.java)
                    adapter.updateData(eventsList)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
    }
}