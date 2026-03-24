package com.example.where2next.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.example.where2next.R
import com.example.where2next.adapters.TicketPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TicketsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tickets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutTickets)
        viewPager = view.findViewById<ViewPager2>(R.id.viewPagerTicketLists)

        //attaching adapter to viewpager
        val adapter = TicketPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) {tab, position ->
            when (position) {
                0 -> tab.text = "Upcoming"
                1 -> tab.text = "Past"
            }
        }.attach()
    }

}