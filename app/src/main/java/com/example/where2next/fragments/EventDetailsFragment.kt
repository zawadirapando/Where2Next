package com.example.where2next.fragments

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.w3c.dom.Text
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EventDetailsFragment : Fragment() {

    private var mapView: MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")

        //initialize map
        mapView = view.findViewById(R.id.mapViewLite)
        mapView?.onCreate(savedInstanceState)

        if (event != null){
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

            //image
            Glide.with(this)
                .load(event.coverImageUrl)
                .centerCrop()
                .into(coverImage)

            //date
            val formatter = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            val dateObject = event.dateAndTime

            val formattedDate = if (dateObject != null){
                formatter.format(dateObject)
            }else{
                "No date yet"
            }

            dateAndDuration.text = "$formattedDate • ${event.duration}"

            //read more function
            descriptionView.text = event.description
            var isExpanded = false

            readMoreButton.setOnClickListener{
                if(isExpanded){
                    descriptionView.maxLines = 3
                    readMoreButton.text = "Read more"
                }else{
                    descriptionView.maxLines = Int.MAX_VALUE
                    readMoreButton.text = "Read less"
                }

                isExpanded = !isExpanded
            }

            //back button
            val backButton = view.findViewById<ImageButton>(R.id.buttonBack)
            backButton.setOnClickListener{
                parentFragmentManager.popBackStack()
            }

            //Google maps
            locationText.text = event.locationName

            mapView?.getMapAsync{ googleMap ->
                val lat = event.locationCoordinates?.latitude?: 0.0
                val lng = event.locationCoordinates?.longitude?: 0.0
                val eventLocation = LatLng(lat, lng)

                googleMap.addMarker(MarkerOptions().position(eventLocation))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(eventLocation, 15f))
            }

            val mapClickListener = View.OnClickListener{
                val encodedLocation = URLEncoder.encode(event.locationName, "UTF-8")
                val uri = Uri.parse("geo:0,0?q=$encodedLocation")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(mapIntent)
            }

            view.findViewById<View>(R.id.mapOverlayClickArea).setOnClickListener(mapClickListener)

            val radarCard = view.findViewById<MaterialCardView>(R.id.cardLiveRadar)
            val radarTitle = view.findViewById<TextView>(R.id.textRadarTitle)
            val radarSubtitle = view.findViewById<TextView>(R.id.textRadarSubtitle)
            val radarIcon = view.findViewById<ImageView>(R.id.iconRadarLock)

            val now = System.currentTimeMillis()
            val eventStartTime = event.dateAndTime?.time ?: Long.MAX_VALUE

            // If right now is past the event start time, UNLOCK it!
            if (now >= eventStartTime) {

                // 1. Set the base active colors (Pink and Dark Red/Grey)
                val color = MaterialColors.getColor(radarCard, com.google.android.material.R.attr.colorTertiary)
                radarCard.strokeColor = Color.parseColor("#E91E63")
                radarCard.setCardBackgroundColor(color)
                radarTitle.text = "Live Radar Active"
                radarTitle.setTextColor(Color.parseColor("#E91E63"))

                val displayCount = if (event.liveAttendanceCount > 0) event.liveAttendanceCount else (50..200).random()
                radarSubtitle.text = "$displayCount people are here. Tap to view map."
                radarSubtitle.setTextColor(Color.WHITE)

                radarIcon.setColorFilter(Color.parseColor("#E91E63"))

                // --- 2. THE PULSE ANIMATION ---
                // This creates a smooth heartbeat by scaling the card down 3% and back up
                val pulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
                    radarCard,
                    PropertyValuesHolder.ofFloat("scaleX", 1.0f, 0.97f),
                    PropertyValuesHolder.ofFloat("scaleY", 1.0f, 0.97f)
                )
                pulseAnimation.duration = 900 // Speed of the pulse (900ms is a relaxed heartbeat)
                pulseAnimation.repeatCount = ObjectAnimator.INFINITE // Keep pulsing forever
                pulseAnimation.repeatMode = ObjectAnimator.REVERSE // Shrink, then grow, then shrink...
                pulseAnimation.start()

                // 3. Make it open the Heatmap
                radarCard.setOnClickListener {
                    val heatmapFragment = HeatmapFragment()

                    val bundle = Bundle()
                    bundle.putParcelable("SELECTED_EVENT", event)

                    heatmapFragment.arguments = bundle

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, heatmapFragment)
                        .addToBackStack(null)
                        .commit()
                }
            } else {
                // LOCKED STATE
                radarCard.setOnClickListener(null)

                // Ensure scale is normal and animations are canceled if they scroll away and back
                radarCard.scaleX = 1.0f
                radarCard.scaleY = 1.0f
                radarCard.clearAnimation()
            }

            //Price bar
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

            val layoutAttendeeBar = view.findViewById<View>(R.id.layoutAttendeeBar)
            val layoutHostBar = view.findViewById<View>(R.id.layoutHostBar)

            if (currentUserId == event.creatorId) {
                // If host
                layoutAttendeeBar.visibility = View.GONE
                layoutHostBar.visibility = View.VISIBLE

                view.findViewById<Button>(R.id.buttonEditEvent).setOnClickListener {
                    // TODO: Open CreateFragment and pass the Event object
                    android.widget.Toast.makeText(context, "Editing Event...", android.widget.Toast.LENGTH_SHORT).show()
                }

                view.findViewById<Button>(R.id.buttonScanTickets).setOnClickListener {
                    // TODO: Open Scanner
                    android.widget.Toast.makeText(context, "Opening Scanner...", android.widget.Toast.LENGTH_SHORT).show()
                }

            } else {
                // If attendee
                layoutAttendeeBar.visibility = View.VISIBLE
                layoutHostBar.visibility = View.GONE

                priceText.text = "Ksh ${event.ticketPrice.toInt()}"

                if (event.ticketsAvailable <= 0){
                    statusText.text = "Sold out"
                    statusText.setTextColor(Color.RED)

                    buyButton.text = "Unavailable"
                    buyButton.isEnabled = false
                    buyButton.setBackgroundColor(Color.DKGRAY)

                }else {
                    val salesEndMillis = event.salesEndDateTime?.time ?: 0L
                    val nowMillis = System.currentTimeMillis()
                    val timeDifference = salesEndMillis - nowMillis

                    val twelveHoursInMillis = 12 * 60 * 60 * 1000L

                    if (salesEndMillis > 0 && timeDifference <= 0) {
                        statusText.text = "Sales closed"
                        statusText.setTextColor(Color.RED)

                        buyButton.text = "Gate only"
                        buyButton.isEnabled = false
                        buyButton.setBackgroundColor(Color.DKGRAY)

                    } else if (salesEndMillis > 0 && timeDifference <= twelveHoursInMillis) {
                        buyButton.text = "Get tickets"
                        buyButton.isEnabled = true

                        buyButton.setOnClickListener {
                            val checkoutFragment = CheckoutFragment()

                            val bundle = Bundle()
                            bundle.putParcelable("SELECTED_EVENT", event)

                            checkoutFragment.arguments = bundle

                            parentFragmentManager.beginTransaction()
                                .replace(R.id.frameLayout, checkoutFragment)
                                .addToBackStack(null)
                                .commit()
                        }

                        object : CountDownTimer(timeDifference, 1000) {
                            @SuppressLint("DefaultLocale")
                            override fun onTick(millisUntilFinished: Long) {
                                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                                statusText.text = String.format(
                                    "%02d:%02d:%02d till end of sale", hours, minutes, seconds)
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
                    } else {
                        statusText.text = "Available now"
                        statusText.setTextColor(Color.parseColor("#4CAF50"))

                        buyButton.isEnabled = true
                        buyButton.text = "Get Tickets"
                    }

                    buyButton.setOnClickListener {
                        val checkoutFragment = CheckoutFragment()

                        val bundle = Bundle()
                        bundle.putParcelable("SELECTED_EVENT", event)

                        checkoutFragment.arguments = bundle

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.frameLayout, checkoutFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.VISIBLE
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}