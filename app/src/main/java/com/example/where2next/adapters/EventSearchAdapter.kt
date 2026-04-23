package com.example.where2next.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.where2next.R
import com.example.where2next.models.Event

class EventSearchAdapter(
    private var events: MutableList<Event>,
    private val onItemClick: (Event) -> Unit
) : RecyclerView.Adapter<EventSearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textEventTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.title.text = event.title
        holder.itemView.setOnClickListener { onItemClick(event) }
    }

    fun updateList(newList: List<Event>) {
        events.clear()
        events.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount() = events.size
}