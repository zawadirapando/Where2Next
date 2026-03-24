package com.example.where2next.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event

class CarouselAdapter(
    private val eventList : List<Event>,
    private val onEventClick : (Event) -> Unit
) : RecyclerView.Adapter<CarouselAdapter.CarouselViewHolder>() {

    class CarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val imageCover : ImageView = itemView.findViewById(R.id.imageCarouselCover)
        val textDate : TextView = itemView.findViewById(R.id.textCarouselDate)
        val textTitle : TextView = itemView.findViewById(R.id.textCarouselTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel_event, parent, false)
        return CarouselViewHolder(view)
    }

    override fun getItemCount(): Int {
        return eventList.size
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        val currentEvent = eventList[position]

        Glide.with(holder.itemView.context)
            .load(currentEvent.coverImageUrl)
            .centerCrop()
            .into(holder.imageCover)

        val formatter = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
        val dateObject = currentEvent.dateAndTime

        if (dateObject != null){
            holder.textDate.text = formatter.format(dateObject)
        }else {
            holder.textDate.text = "No date yet"
        }

        holder.textTitle.text = currentEvent.title

        holder.itemView.setOnClickListener {
            onEventClick(currentEvent)
        }
    }
}