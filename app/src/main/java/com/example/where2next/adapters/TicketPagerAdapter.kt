package com.example.where2next.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.where2next.fragments.PastTicketsFragment
import com.example.where2next.fragments.UpcomingTicketsFragment

class TicketPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
           0 -> UpcomingTicketsFragment()
           1 -> PastTicketsFragment()
           else -> UpcomingTicketsFragment()
        }
    }
}