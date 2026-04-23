    package com.example.where2next.adapters
    
    import android.graphics.Bitmap
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.ImageView
    import android.widget.TextView
    import androidx.recyclerview.widget.RecyclerView
    import com.example.where2next.R
    import com.example.where2next.models.Event
    import com.example.where2next.models.Ticket
    import com.google.zxing.BarcodeFormat
    import com.journeyapps.barcodescanner.BarcodeEncoder
    import java.text.SimpleDateFormat
    import java.util.Locale
    
    class TicketAdapter (
        private val event : Event,
        private val ticket : Ticket,
        private val buyerName : String
    ): RecyclerView.Adapter<TicketAdapter.TicketViewHolder>(){
    
        class TicketViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
            private val textEventTitle: TextView = itemView.findViewById(R.id.textCardEventTitle)
            private val imageQrCode: ImageView = itemView.findViewById(R.id.imageCardQrCode)
            private val textBuyerName: TextView = itemView.findViewById(R.id.textCardBuyerName)
            private val textTicketCount: TextView = itemView.findViewById(R.id.textCardTicketCount)
            private val textDateTime: TextView = itemView.findViewById(R.id.textCardDateTime)
            private val textLocation: TextView = itemView.findViewById(R.id.textCardLocation)
            private val textHost: TextView = itemView.findViewById(R.id.textCardHost)
    
            fun bind(ticket: Ticket, event: Event, buyerName: String, position: Int, totalTickets: Int) {
                //basic
                textEventTitle.text = event.title
                textLocation.text = event.locationName
                textHost.text = event.host
    
                //date
                val formatter = SimpleDateFormat("EEE, MMM dd • h:mm a", Locale.getDefault())
                val dateObject = event.dateAndTime
                textDateTime.text = if (dateObject != null){
                        formatter.format(dateObject)
                    }else{
                        "No date yet"
                    }
                //name, ticket number
                textBuyerName.text = buyerName
                textTicketCount.text = "Ticket ${position+1} of ${totalTickets}"
    
                //QR
                try {
                    val uniqueQRData = "${ticket.ticketId}-${position}"
    
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap : Bitmap = barcodeEncoder.encodeBitmap(
                        uniqueQRData,
                        BarcodeFormat.QR_CODE,
                        800,
                        800
                    )
    
                    imageQrCode.setImageBitmap(bitmap)
                }catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    
        override fun getItemCount(): Int {
            return ticket.quantity
        }
    
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketAdapter.TicketViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ticket_card, parent, false)
            return TicketViewHolder(view)
        }
    
        override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
            holder.bind(ticket, event, buyerName, position, itemCount)
        }
    
    }