package com.example.where2next.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import kotlinx.coroutines.currentCoroutineContext
import java.text.SimpleDateFormat
import java.util.Locale

class WalletAdapter (
    private val ticketList: List<Pair<Ticket, Event>>,
    private val onTicketClick: (Ticket, Event) -> Unit
) : RecyclerView.Adapter<WalletAdapter.WalletViewHolder>() {

    inner class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val imageCover : ImageView = itemView.findViewById(R.id.imageWalletEventCover)
        private val textTitle : TextView = itemView.findViewById(R.id.textWalletEventTitle)
        private val textHost : TextView = itemView.findViewById(R.id.textWalletEventHost)
        private val textDetails : TextView = itemView.findViewById(R.id.textWalletEventDetails)
        private val textQuantity : TextView = itemView.findViewById(R.id.textWalletTicketQuantity)
        private val textButton : TextView = itemView.findViewById(R.id.textViewTicket)

        init {
            itemView.setOnClickListener{ triggerClick() }
            textButton.setOnClickListener { triggerClick() }
        }

        private fun triggerClick() {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION){
                val currentItem = ticketList[position]
                onTicketClick(currentItem.first, currentItem.second)
            }
        }

        fun bind(ticket: Ticket, event: Event) {

            Glide.with(itemView.context)
                .load(event.coverImageUrl)
                .centerCrop()
                .into(imageCover)

            textTitle.text = event.title
            textHost.text = event.host

            val formatter = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
            val dateString = event.dateAndTime?.let { formatter.format(it) } ?: "No date yet"

            textDetails.text = "${event.locationName} • $dateString"

            textQuantity.text = "${ticket.quantity}x ticket"
        }
    }

    override fun getItemCount(): Int {
        return ticketList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallet_card, parent, false)
        return WalletViewHolder(view)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val currentItem = ticketList[position]
        holder.bind(currentItem.first, currentItem.second)
    }


}

