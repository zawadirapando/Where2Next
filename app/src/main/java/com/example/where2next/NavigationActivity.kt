package com.example.where2next

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.where2next.databinding.ActivityNavigationBinding
import com.example.where2next.fragments.CreateFragment
import com.example.where2next.fragments.HomeFragment
import com.example.where2next.fragments.ProfileFragment
import com.example.where2next.fragments.TicketsFragment

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home -> replaceFragment(HomeFragment())
                R.id.create -> replaceFragment(CreateFragment())
                R.id.tickets -> replaceFragment(TicketsFragment())
                R.id.profile -> replaceFragment(ProfileFragment())
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }
}