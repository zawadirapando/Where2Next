package com.example.where2next

import android.os.Bundle
import android.view.View
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

        //keyboard fix
        val rootView = binding.root

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height

            val keyboardHeight = screenHeight - r.bottom

            if(keyboardHeight > screenHeight*0.15) {
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                val currentFrag = supportFragmentManager.findFragmentById(R.id.frameLayout)

                if(currentFrag is HomeFragment ||
                    currentFrag is CreateFragment ||
                    currentFrag is TicketsFragment ||
                    currentFrag is ProfileFragment ){

                    binding.bottomNavigationView.visibility = View.VISIBLE
                }

            }
        }

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