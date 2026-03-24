package com.example.where2next.fragments

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
import org.w3c.dom.Text
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EventDetailsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event = arguments?.getParcelable<Event>("SELECTED_EVENT")

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

            locationText.setOnClickListener{
                val encodedLocation = URLEncoder.encode(event.locationName, "UTF-8")
                val uri = Uri.parse("geo:0,0?q=$encodedLocation")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(mapIntent)
            }

            //Price bar
            priceText.text = "Ksh ${event.ticketPrice.toInt()}"

            if (event.ticketsAvailable <= 0){
                statusText.text = "Sold out"
                statusText.setTextColor(Color.RED)

                buyButton.text = "Unavailable"
                buyButton.isEnabled = false
                buyButton.setBackgroundColor(Color.DKGRAY)

            }else{
                val salesEndMillis = event.salesEndDateTime?.time ?: 0L
                val nowMillis = System.currentTimeMillis()
                val timeDifference = salesEndMillis - nowMillis

                val twelveHoursInMillis = 12*60*60*1000L

                if (salesEndMillis > 0 && timeDifference <=0){
                    statusText.text = "Sales closed"
                    statusText.setTextColor(Color.RED)

                    buyButton.text = "Gate only"
                    buyButton.isEnabled = false
                    buyButton.setBackgroundColor(Color.DKGRAY)

                }else if (salesEndMillis > 0 && timeDifference <= twelveHoursInMillis){
                    buyButton.text = "Get tickets"
                    buyButton.isEnabled = true

                    buyButton.setOnClickListener{
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
                        override fun onTick(millisUntilFinished: Long) {
                            val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)%60
                            val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)%60

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
                }else{
                    statusText.text = "Available now"
                    statusText.setTextColor(Color.parseColor("#4CAF50"))

                    buyButton.isEnabled = true
                    buyButton.text = "Get Tickets"

                    buyButton.setOnClickListener{
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
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
        bottomNav?.visibility = View.VISIBLE
    }
}