package com.example.where2next.fragments

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EventDetailsFragment : Fragment() {

    private var mapView: MapView? = null
    private var eventListener: ListenerRegistration? = null
    private lateinit var db: FirebaseFirestore
    private var currentEventId: String? = null

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(context, "Scan cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val rawScannedData = result.contents.trim()

        // Reject anything that looks like a URL or contains slashes —
        // these can never be valid Firestore document IDs
        if (rawScannedData.contains("://") || rawScannedData.contains("/")) {
            Toast.makeText(context, "Invalid QR code — not a Where2Next ticket", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        val parts = rawScannedData.split("-")
        val actualTicketId = parts[0].trim()

        // Extra safety: Firestore IDs are alphanumeric, reject anything obviously wrong
        if (actualTicketId.isEmpty() || actualTicketId.length < 10) {
            Toast.makeText(context, "Invalid QR code — not a Where2Next ticket", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        db.collection("tickets").document(actualTicketId).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener

                if (!document.exists()) {
                    Toast.makeText(context, "Ticket not found", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val ticketEventId = document.getString("eventId")

                if (ticketEventId == currentEventId) {
                    incrementLiveAttendance()
                    Toast.makeText(context, "Ticket Validated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Invalid Ticket: This ticket is for a different event.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Error verifying ticket: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        val event: Event? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("SELECTED_EVENT", Event::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("SELECTED_EVENT")
        }

        if (event == null) {
            Log.e("EventDetailsFragment", "Event object is null — nothing was passed in the bundle.")
            Toast.makeText(requireContext(), "Error: Event data missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (event.eventId.isEmpty()) {
            Log.w("EventDetailsFragment", "eventId is empty. Live radar and scanning will be disabled.")
        } else {
            currentEventId = event.eventId
        }

        mapView = view.findViewById(R.id.mapViewLite)
        mapView?.onCreate(savedInstanceState)

        // --- 1. BIND ALL UI DATA ---

        val coverImage = view.findViewById<ImageView>(R.id.imageEventCover)
        view.findViewById<TextView>(R.id.textDetailsTitle).text = event.title
        view.findViewById<TextView>(R.id.textDetailsLocation).text = event.locationName
        val dateAndDuration = view.findViewById<TextView>(R.id.textDetailsDateTime)
        view.findViewById<TextView>(R.id.textHost).text = event.host
        val descriptionView = view.findViewById<TextView>(R.id.textDetailsDescription)
        val readMoreButton = view.findViewById<TextView>(R.id.textReadMore)
        val locationText = view.findViewById<TextView>(R.id.textMapPin)
        val priceText = view.findViewById<TextView>(R.id.textStickyPrice)
        val statusText = view.findViewById<TextView>(R.id.textStickyStatus)
        val buyButton = view.findViewById<Button>(R.id.buttonGetTickets)
        val hostAvatar = view.findViewById<ImageView>(R.id.imageHostAvatar)
        val hostInitials = view.findViewById<TextView>(R.id.textHostInitials)

        // FIX 3: Clear default initials immediately so "JD" never flashes
        hostInitials.text = ""

        // Dynamic chips
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupTags)
        chipGroup.removeAllViews()
        event.tags?.let { tags ->
            for (tag in tags) {
                val chip = Chip(requireContext()).apply {
                    text = tag.replace("_", " ").split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    isClickable = false
                    isCheckable = false
                    isFocusable = false
                }
                chipGroup.addView(chip)
            }
        }

        // FIX 3: Fetch host details using creatorId from the event
        if (event.creatorId.isNotEmpty()) {
            db.collection("users").document(event.creatorId).get()
                .addOnSuccessListener { document ->
                    if (!isAdded) return@addOnSuccessListener


                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""
                        val profileImageUrl = document.getString("profileImageUrl")

                        val fullName = "$firstName $lastName".trim()
                        val initials = fullName
                            .split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase()

                        if (!profileImageUrl.isNullOrEmpty()) {
                            hostAvatar.visibility = View.VISIBLE
                            hostInitials.visibility = View.GONE
                            Glide.with(requireContext())  // <-- must be requireContext(), NOT this
                                .load(profileImageUrl)
                                .circleCrop()
                                .into(hostAvatar)
                        } else {
                            hostInitials.text = initials
                            hostInitials.visibility = View.VISIBLE
                            hostAvatar.visibility = View.GONE
                        }
                    }
                }
        }

        // Cover image
        Glide.with(this)
            .load(event.coverImageUrl)
            .centerCrop()
            .into(coverImage)

        // Date formatting
        val formatter = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        val formattedDate = if (event.dateAndTime != null) {
            formatter.format(event.dateAndTime)
        } else {
            "No date yet"
        }
        dateAndDuration.text = "$formattedDate • ${event.duration}"

        // Read more toggle
        descriptionView.text = event.description
        var isExpanded = false
        readMoreButton.setOnClickListener {
            if (isExpanded) {
                descriptionView.maxLines = 3
                readMoreButton.text = "Read more"
            } else {
                descriptionView.maxLines = Int.MAX_VALUE
                readMoreButton.text = "Read less"
            }
            isExpanded = !isExpanded
        }

        // Back button
        view.findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Map
        locationText.text = event.locationName
        mapView?.getMapAsync { googleMap ->
            val lat = event.locationCoordinates?.latitude ?: 0.0
            val lng = event.locationCoordinates?.longitude ?: 0.0
            val eventLocation = LatLng(lat, lng)
            googleMap.addMarker(MarkerOptions().position(eventLocation))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(eventLocation, 15f))
        }

        view.findViewById<View>(R.id.mapOverlayClickArea).setOnClickListener {
            val encodedLocation = URLEncoder.encode(event.locationName, "UTF-8")
            val uri = Uri.parse("geo:0,0?q=$encodedLocation")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        // --- 2. LIVE RADAR ---
        val radarCard = view.findViewById<MaterialCardView>(R.id.cardLiveRadar)
        val radarTitle = view.findViewById<TextView>(R.id.textRadarTitle)
        val radarSubtitle = view.findViewById<TextView>(R.id.textRadarSubtitle)
        val radarIcon = view.findViewById<ImageView>(R.id.iconRadarLock)

        val now = System.currentTimeMillis()
        val eventStartMillis = event.dateAndTime?.time ?: Long.MAX_VALUE

        // FIX 1: Calculate event end time. Duration is stored as e.g. "3 hours".
        // Parse the number out so we can add it to the start time.
        val durationHours = event.duration
            ?.replace(Regex("[^0-9]"), "")
            ?.toLongOrNull() ?: 0L
        val eventEndMillis = eventStartMillis + TimeUnit.HOURS.toMillis(durationHours)

        // FIX 1: Radar is active from event start until 12 hours after event ends
        val radarWindowEndMillis = eventEndMillis + TimeUnit.HOURS.toMillis(12)
        val isRadarActive = now >= eventStartMillis && now < radarWindowEndMillis

        if (isRadarActive && !currentEventId.isNullOrEmpty()) {
            val color = MaterialColors.getColor(radarCard, com.google.android.material.R.attr.colorTertiary)
            radarCard.strokeColor = Color.parseColor("#E91E63")
            radarCard.setCardBackgroundColor(color)
            radarTitle.text = "Live Radar Active"
            radarTitle.setTextColor(Color.parseColor("#E91E63"))
            radarIcon.setColorFilter(Color.parseColor("#E91E63"))

            val pulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
                radarCard,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 0.97f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 0.97f)
            )
            pulseAnimation.duration = 900
            pulseAnimation.repeatCount = ObjectAnimator.INFINITE
            pulseAnimation.repeatMode = ObjectAnimator.REVERSE
            pulseAnimation.start()

            eventListener = db.collection("events").document(currentEventId!!)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val currentAttendance = snapshot.getLong("liveAttendanceCount")?.toInt() ?: 0
                    // FIX 2: Singular vs plural
                    val personLabel = if (currentAttendance == 1) "person" else "people"
                    radarSubtitle.text = "$currentAttendance $personLabel are here."
                    radarSubtitle.setTextColor(Color.WHITE)
                }
        } else {
            // FIX 1: Show a different message if radar has expired vs not started yet
            if (now >= radarWindowEndMillis) {
                radarTitle.text = "Live Radar Ended"
                radarSubtitle.text = "This event has concluded"
            } else {
                radarTitle.text = "Live Radar Locked"
                radarSubtitle.text = "Unlocks when event starts"
            }
            radarCard.setOnClickListener(null)
            radarCard.scaleX = 1.0f
            radarCard.scaleY = 1.0f
            radarCard.clearAnimation()
        }

        // --- 3. HOST VS ATTENDEE ---
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val layoutAttendeeBar = view.findViewById<View>(R.id.layoutAttendeeBar)
        val layoutHostBar = view.findViewById<View>(R.id.layoutHostBar)

        if (currentUserId != null && currentUserId == event.creatorId) {
            layoutAttendeeBar.visibility = View.GONE
            layoutHostBar.visibility = View.VISIBLE

            view.findViewById<Button>(R.id.buttonEditEvent).setOnClickListener {
                val createFragment = CreateFragment()
                val bundle = Bundle()
                bundle.putParcelable("EDIT_EVENT", event)
                createFragment.arguments = bundle
                parentFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, createFragment)
                    .addToBackStack(null)
                    .commit()
            }

            val scanButton = view.findViewById<Button>(R.id.buttonScanTickets)
            if (currentEventId.isNullOrEmpty()) {
                scanButton.isEnabled = false
                scanButton.text = "Scan unavailable"
            } else {
                scanButton.setOnClickListener {
                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    options.setPrompt("Scan a ticket QR Code")
                    options.setCameraId(0)
                    options.setBeepEnabled(true)
                    options.setBarcodeImageEnabled(false)
                    options.setOrientationLocked(false)
                    barcodeLauncher.launch(options)
                }
            }

        } else {
            layoutAttendeeBar.visibility = View.VISIBLE
            layoutHostBar.visibility = View.GONE

            priceText.text = "Ksh ${event.ticketPrice.toInt()}"

            if (event.ticketsAvailable <= 0) {
                statusText.text = "Sold out"
                statusText.setTextColor(Color.RED)
                buyButton.text = "Unavailable"
                buyButton.isEnabled = false
                buyButton.setBackgroundColor(Color.DKGRAY)
            } else {
                val salesEndMillis = event.salesEndDateTime?.time ?: 0L
                val nowMillis = System.currentTimeMillis()
                val timeDifference = salesEndMillis - nowMillis
                val twelveHoursInMillis = 12 * 60 * 60 * 1000L

                when {
                    salesEndMillis > 0 && timeDifference <= 0 -> {
                        statusText.text = "Sales closed"
                        statusText.setTextColor(Color.RED)
                        buyButton.text = "Gate only"
                        buyButton.isEnabled = false
                        buyButton.setBackgroundColor(Color.DKGRAY)
                    }
                    salesEndMillis > 0 && timeDifference <= twelveHoursInMillis -> {
                        buyButton.text = "Get tickets"
                        buyButton.isEnabled = true
                        buyButton.setOnClickListener { navigateToCheckout(event) }

                        object : CountDownTimer(timeDifference, 1000) {
                            @SuppressLint("DefaultLocale")
                            override fun onTick(millisUntilFinished: Long) {
                                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                                statusText.text = String.format("%02d:%02d:%02d till end of sale", hours, minutes, seconds)
                                statusText.setTextColor(Color.parseColor("#FF9800"))
                            }
                            override fun onFinish() {
                                statusText.text = "Sales closed"
                                statusText.setTextColor(Color.RED)
                                buyButton.text = "Gate only"
                                buyButton.isEnabled = false
                                buyButton.setBackgroundColor(Color.DKGRAY)
                            }
                        }.start()
                    }
                    else -> {
                        statusText.text = "Available now"
                        statusText.setTextColor(Color.parseColor("#4CAF50"))
                        buyButton.isEnabled = true
                        buyButton.text = "Get Tickets"
                        buyButton.setOnClickListener { navigateToCheckout(event) }
                    }
                }
            }
        }
    }

    private fun navigateToCheckout(event: Event) {
        val checkoutFragment = CheckoutFragment()
        val bundle = Bundle()
        bundle.putParcelable("SELECTED_EVENT", event)
        checkoutFragment.arguments = bundle
        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, checkoutFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun incrementLiveAttendance() {
        currentEventId?.let { eventId ->
            db.collection("events").document(eventId)
                .update("liveAttendanceCount", FieldValue.increment(1))
                .addOnSuccessListener {
                    Toast.makeText(context, "✓ Ticket Valid! Attendee Checked In.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to check-in: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eventListener?.remove()
        mapView?.onDestroy()
        mapView = null
        requireActivity().findViewById<View>(R.id.bottomNavigationView)?.visibility = View.VISIBLE
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}