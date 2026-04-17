package com.example.where2next.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.example.where2next.adapters.WalletAdapter
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class PastTicketsFragment : Fragment(R.layout.fragment_past_tickets) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var walletAdapter: WalletAdapter

    private val ticketList = mutableListOf<Pair<Ticket,Event>>()

    private val db  = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewPastTickets)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        walletAdapter = WalletAdapter(
            ticketList = ticketList,
            onTicketClick = {clickedTicket, clickedEvent ->

                navigateToTicketReceipt(clickedTicket, clickedEvent)
            }
        )

        recyclerView.adapter = walletAdapter

        fetchUserTickets()
    }

    private fun navigateToTicketReceipt(clickedTicket: Ticket, clickedEvent : Event){
        val ticketFragment = TicketReceiptFragment()

        val bundle = Bundle()

        bundle.putParcelable("PURCHASED_TICKET", clickedTicket)
        bundle.putParcelable("SELECTED_EVENT", clickedEvent)

        ticketFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, ticketFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun fetchUserTickets() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("tickets")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { ticketSnapshot ->

                // If there are no tickets, clear and notify immediately
                if (ticketSnapshot.isEmpty) {
                    ticketList.clear()
                    walletAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                val temporaryList = mutableListOf<Pair<Ticket, Event>>()
                val totalTicketsToProcess = ticketSnapshot.size()
                var processedCount = 0

                // Helper function to handle the final UI update safely
                fun checkAndUpdateAdapter() {
                    if (processedCount == totalTicketsToProcess) {
                        // Update the main list and notify ONLY when everything is done
                        ticketList.clear()
                        ticketList.addAll(temporaryList)
                        walletAdapter.notifyDataSetChanged()
                    }
                }

                for (document in ticketSnapshot.documents) {
                    val ticket = document.toObject(Ticket::class.java)

                    if (ticket != null) {
                        db.collection("events").document(ticket.eventId).get()
                            .addOnSuccessListener { eventSnapshot ->
                                val event = eventSnapshot.toObject(Event::class.java)

                                if (event != null) {
                                    val currentDate = Date()
                                    if (event.dateAndTime != null && event.dateAndTime!!.after(currentDate)) {
                                        temporaryList.add(Pair(ticket, event))
                                    }
                                }

                                processedCount++
                                checkAndUpdateAdapter() // Check if we are done
                            }
                            .addOnFailureListener {
                                Log.e("Wallet", "Failed to find matching event")
                                processedCount++
                                checkAndUpdateAdapter() // Must check here too!
                            }
                    } else {
                        processedCount++
                        checkAndUpdateAdapter() // And must check here!
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Wallet", "Failed to download tickets", it)
            }
    }
}