package com.example.where2next.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderDetailsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")
        val ticket = arguments?.getParcelable<Ticket>("PURCHASED_TICKET")

        val textOrderNumber = view.findViewById<TextView>(R.id.textOrderNumber)
        val textOrderTicketType = view.findViewById<TextView>(R.id.textOrderTicketType)
        val textOrderTotal = view.findViewById<TextView>(R.id.textOrderTotal)
        // ✅ NEW: bind the two new TextViews added to the XML
        val textOrderDate = view.findViewById<TextView>(R.id.textOrderDate)
        val textOrderTime = view.findViewById<TextView>(R.id.textOrderTime)
        val buttonBackToReceipt = view.findViewById<Button>(R.id.buttonBackToReceipt)

        if (ticket != null && event != null) {
            textOrderNumber.text = "#${ticket.ticketId.take(8).uppercase()}"
            textOrderTicketType.text = "General Admission"
            textOrderTotal.text = "KES ${ticket.totalPaid}"


            val purchaseDate: Date? = ticket.purchaseTimestamp?.toDate()

            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            if (purchaseDate != null) {
                textOrderDate.text = dateFormat.format(purchaseDate)
                textOrderTime.text = timeFormat.format(purchaseDate)
            } else {
                val now = Date()
                textOrderDate.text = dateFormat.format(now)
                textOrderTime.text = timeFormat.format(now)
            }
        }

        buttonBackToReceipt.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }
}