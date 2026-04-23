package com.example.where2next.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.where2next.R
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions

class PaymentFragment : Fragment(R.layout.fragment_payment) {

    private lateinit var functions: FirebaseFunctions
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        functions = FirebaseFunctions.getInstance("us-central1")

        val event: Event? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("SELECTED_EVENT", Event::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("SELECTED_EVENT")
        }

        val ticket: Ticket? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("PURCHASED_TICKET", Ticket::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("PURCHASED_TICKET")
        }

        if (event == null) return

        val textEventTitle = view.findViewById<TextView>(R.id.textCheckoutEventTitle)
        val textQtyDescription = view.findViewById<TextView>(R.id.textCheckoutQuantityDescription)
        val textPrice = view.findViewById<TextView>(R.id.textCheckoutPrice)
        val textTotal = view.findViewById<TextView>(R.id.textCheckoutTotal)
        val buttonPay = view.findViewById<Button>(R.id.buttonPayNow)
        val phoneInput = view.findViewById<TextInputEditText>(R.id.inputCheckoutPhone)
        val loadingOverlay = view.findViewById<View>(R.id.layoutPaymentProcessing)

        textEventTitle.text = event.title
        textPrice.text = "Ksh ${event.ticketPrice.toInt()}"

        val quantityToBuy = ticket?.quantity ?: 1
        val amountToPay = ticket?.totalPaid ?: event.ticketPrice

        textQtyDescription.text = "${quantityToBuy}x General Admission"
        textTotal.text = "Ksh ${amountToPay.toInt()}"
        buttonPay.text = "Pay Ksh ${amountToPay.toInt()}"

        buttonPay.setOnClickListener {
            val phoneSuffix = phoneInput.text.toString().trim()

            if (phoneSuffix.isEmpty() || phoneSuffix.length < 9) {
                phoneInput.error = "Enter remaining 9 digits (e.g. 7XXXXXXXX)"
                return@setOnClickListener
            }

            val phone = "254$phoneSuffix"

            loadingOverlay.visibility = View.VISIBLE
            buttonPay.isEnabled = false

            val data = hashMapOf(
                "phoneNumber" to phone,
                "eventId" to event.eventId,
                "amount" to amountToPay,
                "userId" to auth.currentUser?.uid,
                "quantity" to quantityToBuy
            )

            functions.getHttpsCallable("initiateStkPush")
                .call(data)
                .addOnSuccessListener { result ->
                    if (!isAdded) return@addOnSuccessListener

                    val resultMap = result.getData() as? Map<String, Any>
                    val checkoutRequestId = resultMap?.get("checkoutRequestId") as? String

                    if (checkoutRequestId != null) {
                        listenForPaymentSuccess(checkoutRequestId, event, quantityToBuy, amountToPay)
                    } else {
                        loadingOverlay.visibility = View.GONE
                        buttonPay.isEnabled = true
                        Toast.makeText(requireContext(), "Payment Error. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener

                    loadingOverlay.visibility = View.GONE
                    buttonPay.isEnabled = true
                    Log.e("Payment", "Function failed", e)
                    Toast.makeText(requireContext(), "Network Error. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun listenForPaymentSuccess(
        checkoutRequestId: String,
        event: Event,
        quantity: Int,
        totalPaid: Double
    ) {
        // Capture view references before the async listener fires
        val loadingOverlay = requireView().findViewById<View>(R.id.layoutPaymentProcessing)
        val buttonPay = requireView().findViewById<Button>(R.id.buttonPayNow)

        var listenerRegistration: ListenerRegistration? = null

        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            listenerRegistration?.remove()
            if (!isAdded) return@Runnable  // <- guard added here too

            loadingOverlay.visibility = View.GONE
            buttonPay.isEnabled = true
            Toast.makeText(requireContext(), "Payment timed out. Check your M-Pesa and try again.", Toast.LENGTH_LONG).show()
        }
        timeoutHandler.postDelayed(timeoutRunnable, 60000)

        listenerRegistration = db.collection("transactions").document(checkoutRequestId)
            .addSnapshotListener { snapshot, e ->
                // Always guard first — this fires asynchronously and fragment may be gone
                if (!isAdded) {
                    listenerRegistration?.remove()
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    return@addSnapshotListener
                }

                if (e != null || snapshot == null) return@addSnapshotListener

                val status = snapshot.getString("status")

                if (status == "SUCCESS") {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    listenerRegistration?.remove()

                    val newTicketId = snapshot.getString("ticketId") ?: checkoutRequestId

                    val finalizedTicket = Ticket(
                        ticketId = newTicketId,
                        eventId = event.eventId,
                        userId = auth.currentUser?.uid ?: "",
                        quantity = quantity,
                        totalPaid = totalPaid
                    )

                    loadingOverlay.visibility = View.GONE

                    parentFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )

                    val receiptFragment = TicketReceiptFragment()
                    receiptFragment.arguments = Bundle().apply {
                        putParcelable("SELECTED_EVENT", event)
                        putParcelable("PURCHASED_TICKET", finalizedTicket)
                    }

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, receiptFragment)
                        .commit()

                } else if (status == "FAILED") {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    listenerRegistration?.remove()
                    loadingOverlay.visibility = View.GONE
                    buttonPay.isEnabled = true
                    Toast.makeText(requireContext(), "Payment Failed or Cancelled.", Toast.LENGTH_LONG).show()
                }
            }
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