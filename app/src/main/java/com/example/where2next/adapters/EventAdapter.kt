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

class EventAdapter(
    private val eventList : List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
        val imageCover : ImageView = itemView.findViewById(R.id.imageEventCover)
        val textTitle : TextView = itemView.findViewById(R.id.textEventTitle)
        val textHost : TextView = itemView.findViewById(R.id.textEventHost)
        val textDetails : TextView = itemView.findViewById(R.id.textEventDetails)
        val textAttendance : TextView = itemView.findViewById(R.id.textEventAttendance)
        val textPrice : TextView = itemView.findViewById(R.id.textEventPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_card, parent, false)
        return EventViewHolder(view)
    }

    override fun getItemCount(): Int {
        return eventList.size
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val currentEvent = eventList[position]

        Glide.with(holder.itemView.context)
            .load(currentEvent.coverImageUrl)
            .placeholder(R.color.light_gray)
            .centerCrop()
            .into(holder.imageCover)

        holder.textTitle.text = currentEvent.title
        holder.textHost.text = currentEvent.host
        holder.textDetails.text = "${currentEvent.locationName} • ${currentEvent.duration}"
        holder.textAttendance.text = "${currentEvent.liveAttendanceCount} attending right now"
        holder.textPrice.text = "Ksh. ${currentEvent.ticketPrice}"

        holder.itemView.setOnClickListener {
            onEventClick(currentEvent)
        }
    }
}