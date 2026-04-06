package com.example.where2next.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event

class MyEventsAdapter(
    private var eventsList: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<MyEventsAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageThumb: ImageView = view.findViewById(R.id.imageEventThumb)
        val textTitle: TextView = view.findViewById(R.id.textEventTitle)
        val textSales: TextView = view.findViewById(R.id.textEventSales)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventsList[position]

        holder.textTitle.text = event.title

        // Calculate tickets sold
        val sold = (event.totalCapacity ?: 0) - event.ticketsAvailable
        holder.textSales.text = "$sold / ${event.totalCapacity} Sold"

        Glide.with(holder.itemView.context)
            .load(event.coverImageUrl)
            .centerCrop()
            .into(holder.imageThumb)

        holder.itemView.setOnClickListener {
            onEventClick(event)
        }
    }

    override fun getItemCount() = eventsList.size

    fun updateData(newEvents: List<Event>) {
        eventsList = newEvents
        notifyDataSetChanged()
    }
}