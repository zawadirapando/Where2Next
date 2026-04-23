package com.example.where2next.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.example.where2next.adapters.WalletAdapter
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class UpcomingTicketsFragment : Fragment(R.layout.fragment_upcoming_tickets) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var walletAdapter: WalletAdapter
    private lateinit var emptyStateText: TextView
    private lateinit var shimmerTickets: ShimmerFrameLayout

    private val ticketList = mutableListOf<Pair<Ticket,Event>>()

    private val db  = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyStateText = view.findViewById(R.id.textNoUpcomingTickets)
        shimmerTickets = view.findViewById(R.id.shimmerTickets)

        recyclerView = view.findViewById(R.id.recyclerViewUpcomingTickets)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        walletAdapter = WalletAdapter(
            ticketList = ticketList,
            onTicketClick = { clickedTicket, clickedEvent ->
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

                // If there are no tickets, stop shimmer and show empty state immediately
                if (ticketSnapshot.isEmpty) {
                    hideShimmer()
                    ticketList.clear()
                    walletAdapter.notifyDataSetChanged()
                    emptyStateText.visibility = View.VISIBLE
                    recyclerView.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val temporaryList = mutableListOf<Pair<Ticket, Event>>()
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
                                    if (event.dateAndTime != null && event.dateAndTime!!.after(currentDate)) {
                                        temporaryList.add(Pair(ticket, event))
                                    }
                                }
                                processedCount++
                                checkAndUpdateAdapter(processedCount, totalTicketsToProcess, temporaryList)
                            }
                            .addOnFailureListener {
                                Log.e("Wallet", "Failed to find matching event")
                                processedCount++
                                checkAndUpdateAdapter(processedCount, totalTicketsToProcess, temporaryList)
                            }
                    } else {
                        processedCount++
                        checkAndUpdateAdapter(processedCount, totalTicketsToProcess, temporaryList)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Wallet", "Failed to download tickets", it)
                hideShimmer()
            }
    }

    private fun checkAndUpdateAdapter(processedCount: Int, total: Int, tempList: MutableList<Pair<Ticket, Event>>) {
        if (processedCount == total) {
            hideShimmer()
            recyclerView.visibility = View.VISIBLE

            val deduplicated = tempList.distinctBy { it.first.ticketId } // deduplicate by ticketId

            val sorted = deduplicated.sortedByDescending {
                it.first.purchaseTimestamp?.toDate()?.time ?: 0L
            }

            ticketList.clear()
            ticketList.addAll(deduplicated)
            walletAdapter.notifyDataSetChanged()

            emptyStateText.visibility = if (ticketList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun hideShimmer() {
        shimmerTickets.stopShimmer()
        shimmerTickets.visibility = View.GONE
    }
}