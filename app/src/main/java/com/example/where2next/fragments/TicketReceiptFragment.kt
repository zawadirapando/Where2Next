package com.example.where2next.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.where2next.R
import com.example.where2next.adapters.TicketAdapter
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore

class TicketReceiptFragment : Fragment() {
    private lateinit var viewPagerTickets: ViewPager2
    private lateinit var tabLayoutDots: TabLayout
    private lateinit var buttonBackToHome: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ticket_receipt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")
        val ticket = arguments?.getParcelable<Ticket>("PURCHASEDx`_TICKET")

        viewPagerTickets = view.findViewById(R.id.viewPagerTickets)
        tabLayoutDots = view.findViewById(R.id.tabLayoutTicketDots)
        buttonBackToHome = view.findViewById(R.id.buttonBackToHome)

        if (event != null && ticket != null){
            fetchBuyerNameAndSetupUI(ticket, event)
        }else {
            Toast.makeText(requireContext(), "Error loading ticket data", Toast.LENGTH_SHORT).show()
        }

        buttonBackToHome.setOnClickListener {
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun fetchBuyerNameAndSetupUI(ticket: Ticket, event: Event){
        db.collection("users").document(ticket.userId).get()
            .addOnSuccessListener { document ->
                val firstName = document.getString("firstName") ?: ""
                val lastName = document.getString("lastName") ?: ""

                val combinedName = "$firstName $lastName".trim()
                
                val buyerName = if (combinedName.isNotEmpty()) combinedName else "Ticket Holder"

                setupCarousel(ticket, event, buyerName)
            }
            .addOnFailureListener{
                setupCarousel(ticket, event, "Ticket Holder")
            }
    }

    private fun setupCarousel(ticket: Ticket, event: Event, buyerName: String){
        val adapter = TicketAdapter(event, ticket, buyerName)

        viewPagerTickets.adapter = adapter

        TabLayoutMediator(tabLayoutDots, viewPagerTickets) {tab, position ->

        }.attach()
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