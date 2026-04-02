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

        bundle.putParcelable("SELECTED_TICKET", clickedTicket)
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

                ticketList.clear()

                if (ticketSnapshot.isEmpty) {
                    walletAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                // 1. Create a temporary list to hold the data while it downloads
                val temporaryList = mutableListOf<Pair<Ticket, Event>>()

                // 2. Track how many documents we need to download
                val totalTicketsToProcess = ticketSnapshot.size()
                var processedCount = 0

                for (document in ticketSnapshot.documents) {
                    val ticket = document.toObject(Ticket::class.java)

                    if (ticket != null) {
                        db.collection("events").document(ticket.eventId).get()
                            .addOnSuccessListener { eventSnapshot ->
                                val event = eventSnapshot.toObject(Event::class.java)

                                if (event != null) {
                                    val currentDate = Date()

                                    // THE BOUNCER: Only let it in if the event is in the PAST
                                    if (event.dateAndTime != null && event.dateAndTime!!.before(currentDate)) {
                                        temporaryList.add(Pair(ticket, event))
                                    }
                                }

                                // 3. Every time a download finishes, tick the counter
                                processedCount++

                                // 4. Only update the UI when ALL downloads are 100% finished
                                if (processedCount == totalTicketsToProcess) {

                                    // Move everything from the temporary list into the real list
                                    ticketList.addAll(temporaryList)

                                    // Poke the Adapter exactly ONE time
                                    walletAdapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener {
                                processedCount++ // Still tick the counter so we don't get stuck forever
                                Log.e("Wallet", "Failed to find matching event")
                            }
                    } else {
                        processedCount++ // Tick the counter if the ticket itself was broken
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Wallet", "Failed to download tickets", it)
            }
    }
}