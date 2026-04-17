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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.jvm.Throws

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

        if (event !=null) {
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

            //back button
            backButton.setOnClickListener{
                parentFragmentManager.popBackStack()
            }

            //Initial event title, price
            eventNameText.text = event.title
            pricePerTicketText.text = "Ksh ${ticketPrice.toInt()}"

            //timestamp
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateObject = event.salesEndDateTime

            if (dateObject != null){
                val formattedDate = formatter.format(dateObject)
                salesEndText.text = "Sales end on $formattedDate"
            }else{
                salesEndText.text = "Purchase tickets"
            }

            //Initial total
            updateTotal(quantityText, totalPriceText)

            //Plus button
            buttonPlus.setOnClickListener{
                if (ticketQuantity < event.ticketsAvailable){
                    ticketQuantity++
                    updateTotal(quantityText, totalPriceText)
                }
            }

            //Minus button
            buttonMinus.setOnClickListener{
                if (ticketQuantity > 1){
                    ticketQuantity--
                    updateTotal(quantityText, totalPriceText)
                }
            }

            //Proceed payment button
            buttonProceedPayment.setOnClickListener{
                buttonProceedPayment.isEnabled = false
                buttonProceedPayment.text = "Processing..."

                val db = FirebaseFirestore.getInstance()
                val eventRef = db.collection("events").document(event.eventId)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "mock_user_123"

                db.runTransaction { transaction ->
                    val snapshot = transaction.get(eventRef)
                    val currentAvailable = snapshot.getLong("ticketsAvailable") ?: 0

                    if (currentAvailable < ticketQuantity){
                        throw Exception ("Not enough tickets left")
                    }

                    val newAvailable = currentAvailable - ticketQuantity
                    transaction.update(eventRef, "ticketsAvailable", newAvailable)

                    val newTicketRef = db.collection("tickets").document()
                    val newTicket = Ticket(
                        ticketId = newTicketRef.id,
                        eventId = event.eventId,
                        userId = currentUserId,
                        quantity = ticketQuantity,
                        totalPaid = ticketQuantity * ticketPrice,
                        purchaseTimestamp = Timestamp.now(),
                        qrPayload = UUID.randomUUID().toString(),
                        isScanned = false,
                        dynamicSeed = UUID.randomUUID().toString()
                    )

                    transaction.set(newTicketRef, newTicket)

                    newTicket

                }.addOnSuccessListener { mintedTicket ->
                    Toast.makeText(requireContext(), "Payment successful!", Toast.LENGTH_SHORT).show()

                    val paymentFragment = PaymentFragment()

                    val bundle = Bundle()
                    bundle.putParcelable("SELECTED_EVENT", event)
                    bundle.putParcelable("PURCHASED_TICKET", mintedTicket)

                    paymentFragment.arguments = bundle

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, paymentFragment)
                        .addToBackStack(null)
                        .commit()

                }.addOnFailureListener { e ->
                    buttonProceedPayment.isEnabled = true
                    buttonProceedPayment.text = "Proceed to payment"
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()

                }
            }
        }
    }

    private fun updateTotal(quantityText: TextView, totalPriceText: TextView){
        quantityText.text = ticketQuantity.toString()
        val total = ticketQuantity * ticketPrice
        totalPriceText.text = "Ksh $total"
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.VISIBLE
    }
}