package com.nafsshield.ui.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nafsshield.R
import com.nafsshield.ui.keywords.KeywordsFragment
import com.nafsshield.ui.websites.WebsitesFragment

class FilterFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_filter, container, false)
        val viewPager = view.findViewById<ViewPager2>(R.id.filterViewPager)
        val tabLayout = view.findViewById<TabLayout>(R.id.filterTabLayout)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int) = when (position) {
                0 -> KeywordsFragment()
                else -> WebsitesFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "🔤 কীওয়ার্ড"
                else -> "🌐 ওয়েবসাইট"
            }
        }.attach()

        return view
    }
}
