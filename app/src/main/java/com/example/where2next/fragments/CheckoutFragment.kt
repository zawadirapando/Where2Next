package com.example.where2next.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class CheckoutFragment : Fragment() {
    private var ticketQuantity = 1
    private var ticketPrice = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_checkout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")

        if (event != null) {
            ticketPrice = event.ticketPrice

            val eventNameText = view.findViewById<TextView>(R.id.textTicketTitle)
            val pricePerTicketText = view.findViewById<TextView>(R.id.textCheckoutPricePerTicket)
            val salesEndText = view.findViewById<TextView>(R.id.textSalesEnd)
            val quantityText = view.findViewById<TextView>(R.id.textTicketQuantity)
            val totalPriceText = view.findViewById<TextView>(R.id.textTotal)
            val buttonMinus = view.findViewById<MaterialCardView>(R.id.buttonMinus)
            val buttonPlus = view.findViewById<MaterialCardView>(R.id.buttonPlus)
            val backButton = view.findViewById<ImageButton>(R.id.backButton)
            val buttonProceedPayment = view.findViewById<Button>(R.id.buttonProceedPayment)

            backButton.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            eventNameText.text = event.title
            pricePerTicketText.text = "Ksh ${ticketPrice.toInt()}"

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateObject = event.salesEndDateTime
            if (dateObject != null) {
                salesEndText.text = "Sales end on ${formatter.format(dateObject)}"
            } else {
                salesEndText.text = "Purchase tickets"
            }

            updateTotal(quantityText, totalPriceText)

            buttonPlus.setOnClickListener {
                if (ticketQuantity < event.ticketsAvailable) {
                    ticketQuantity++
                    updateTotal(quantityText, totalPriceText)
                }
            }

            buttonMinus.setOnClickListener {
                if (ticketQuantity > 1) {
                    ticketQuantity--
                    updateTotal(quantityText, totalPriceText)
                }
            }

            // ── Just navigate to PaymentFragment — NO ticket creation here ──
            // Ticket is created by the backend ONLY after successful M-Pesa payment
            buttonProceedPayment.setOnClickListener {
                val totalAmount = ticketQuantity * ticketPrice

                // Build a temporary ticket object just to carry quantity + amount to PaymentFragment
                val tempTicket = Ticket(
                    ticketId = "",
                    eventId = event.eventId,
                    userId = "",
                    quantity = ticketQuantity,
                    totalPaid = totalAmount
                )

                val paymentFragment = PaymentFragment()
                val bundle = Bundle()
                bundle.putParcelable("SELECTED_EVENT", event)
                bundle.putParcelable("PURCHASED_TICKET", tempTicket)
                paymentFragment.arguments = bundle

                parentFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, paymentFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun updateTotal(quantityText: TextView, totalPriceText: TextView) {
        quantityText.text = ticketQuantity.toString()
        val total = ticketQuantity * ticketPrice
        totalPriceText.text = "Ksh ${total.toInt()}"
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
    }
}