package com.example.where2next.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class PaymentFragment : Fragment(R.layout.fragment_payment) { // Assuming you used my XML

        private lateinit var functions: FirebaseFunctions
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        functions = FirebaseFunctions.getInstance()

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")
        // Note: The ticket object will be fully created by the backend,
        // we might not need the incoming one unless it has quantity info.

        if (event == null) return

        // Bind UI
        val textEventTitle = view.findViewById<TextView>(R.id.textCheckoutEventTitle)
        val textPrice = view.findViewById<TextView>(R.id.textCheckoutPrice)
        val textTotal = view.findViewById<TextView>(R.id.textCheckoutTotal)
        val buttonPay = view.findViewById<Button>(R.id.buttonPayNow)
        val phoneInput = view.findViewById<TextInputEditText>(R.id.inputCheckoutPhone)
        val loadingOverlay = view.findViewById<View>(R.id.layoutPaymentProcessing)

        // Set UI Data
        textEventTitle.text = event.title
        textPrice.text = "Ksh ${event.ticketPrice.toInt()}"
        textTotal.text = "Ksh ${event.ticketPrice.toInt()}"
        buttonPay.text = "Pay Ksh ${event.ticketPrice.toInt()}"

        buttonPay.setOnClickListener {
            val phone = phoneInput.text.toString().trim()

            if (phone.isEmpty() || phone.length < 9) {
                phoneInput.error = "Enter a valid M-Pesa number"
                return@setOnClickListener
            }

            // 1. Lock the UI so they can't click pay twice
            loadingOverlay.visibility = View.VISIBLE

            // 2. Prepare data for the Cloud Function
            val data = hashMapOf(
                "phoneNumber" to phone,
                "eventId" to event.eventId,
                "amount" to event.ticketPrice,
                "userId" to auth.currentUser?.uid
            )

            // 3. Call the backend to trigger the STK Push
            functions.getHttpsCallable("initiateStkPush")
                .call(data)
                .addOnSuccessListener { result ->
                    // The prompt is now on the user's phone!
                    val resultMap = result.data as? Map<String, Any>
                    val checkoutRequestId = resultMap?.get("checkoutRequestId") as? String

                    if (checkoutRequestId != null) {
                        // 4. Start listening to the database for the webhook result
                        listenForPaymentSuccess(checkoutRequestId, event)
                    } else {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(context, "Payment Error", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    loadingOverlay.visibility = View.GONE
                    Log.e("Payment", "Function failed", e)
                    Toast.makeText(context, "Network Error. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun listenForPaymentSuccess(checkoutRequestId: String, event: Event) {
        val loadingOverlay = requireView().findViewById<View>(R.id.layoutPaymentProcessing)

        // Listen to the specific transaction document the backend just created
        val listenerRegistration = db.collection("transactions").document(checkoutRequestId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }

                val status = snapshot.getString("status")

                if (status == "SUCCESS") {
                    // PAYMENT WENT THROUGH!
                    val newTicketId = snapshot.getString("ticketId") ?: ""

                    // Build a ticket object to pass to the receipt screen
                    val finalizedTicket = Ticket(
                        ticketId = newTicketId,
                        eventId = event.eventId,
                        userId = auth.currentUser?.uid ?: "",
                        quantity = 1
                    )

                    loadingOverlay.visibility = View.GONE

                    // Navigate to Success Receipt
                    val receiptFragment = TicketReceiptFragment()
                    val bundle = Bundle().apply {
                        putParcelable("SELECTED_EVENT", event)
                        putParcelable("PURCHASED_TICKET", finalizedTicket)
                    }
                    receiptFragment.arguments = bundle

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, receiptFragment) // Use your main container ID
                        .commit() // Don't addToBackStack so they can't press back to "Pay" again

                } else if (status == "FAILED") {
                    // User cancelled the PIN prompt or had insufficient funds
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(context, "Payment Failed or Cancelled.", Toast.LENGTH_LONG).show()
                }
            }

        // Optional: You could add a Handler to timeout and cancel the listener after 60 seconds
    }
}