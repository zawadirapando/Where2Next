package com.example.where2next.fragments

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.where2next.R
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.GeoPoint
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CreateFragment : Fragment(R.layout.fragment_create) {

    private lateinit var placesClient: PlacesClient
    private lateinit var locationAdapter: ArrayAdapter<String>
    private val locationNamesList = mutableListOf<String>()

    private val placeIdMap = mutableMapOf<String, String>()

    private var eventLatitude: Double = 0.0
    private var eventLongitude: Double = 0.0

    private var selectedImageUri: Uri? = null
    private val eventCalendar = Calendar.getInstance()
    private val salesCalendar = Calendar.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = listOf("Live Music", "Tech", "Food", "Art", "Sports", "Networking", "Nightlife", "Workshops")

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCreate)

        for (category in categories) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                
                setChipBackgroundColorResource(R.color.chip_background_state)
            }
            chipGroup.addView(chip)
        }

        val buttonUploadImage = view.findViewById<Button>(R.id.buttonUploadImage)
        buttonUploadImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val buttonPublish = view.findViewById<Button>(R.id.buttonPublish)
        buttonPublish.setOnClickListener {
            publishEvent(view)
        }

        setLocationDropdown(view)
        setUpDateAndTimePickers(view)
    }

    //image
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri

            val imageView = requireView().findViewById<ImageView>(R.id.imageEventCover)

            imageView.setImageURI(uri)

            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            imageView.imageTintList = null
        }
    }

    //location dropdown
    private fun setLocationDropdown(view : View) {
        val locationInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.editLocation)

        if (!Places.isInitialized()){
            Places.initialize(requireContext(), com.example.where2next.BuildConfig.MAPS_API_KEY)
        }

        placesClient = Places.createClient(requireContext())

        locationAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            locationNamesList
        )
        locationInput.setAdapter(locationAdapter)

        //keystrokes
        locationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()

                if (query.length > 0){
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            locationNamesList.clear()
                            placeIdMap.clear()

                            for (prediction in response.autocompletePredictions) {
                                val cityName = prediction.getFullText(null).toString()
                                locationNamesList.add(cityName)
                                placeIdMap[cityName] = prediction.placeId
                            }

                            val newAdapter = ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                locationNamesList
                            )
                            locationInput.setAdapter(newAdapter)

                            locationInput.showDropDown()
                        }
                        .addOnFailureListener{ e->
                            Toast.makeText(requireContext(), "Google Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        })

        locationInput.setOnItemClickListener{parent, view, position, id ->
            val selectedCityName = locationNamesList[position]
            val hiddenPlaceId = placeIdMap[selectedCityName]

            if(hiddenPlaceId != null){
                val placeFields = listOf(Place.Field.LAT_LNG)
                val request = FetchPlaceRequest.newInstance(hiddenPlaceId, placeFields)

                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                    if (response.place.latLng != null){
                        eventLatitude = response.place.latLng!!.latitude
                        eventLongitude = response.place.latLng!!.longitude
                    }
                }
            }
        }
    }

    //Date and Time
    private fun setUpDateAndTimePickers(view: View){
        val editEventDate = view.findViewById<TextInputEditText>(R.id.editEventDate)
        val editEventTime = view.findViewById<TextInputEditText>(R.id.editEventTime)

        val editSalesDate = view.findViewById<TextInputEditText>(R.id.editSalesEndDate)
        val editSalesTime = view.findViewById<TextInputEditText>(R.id.editSalesEndTime)

        //event click listeners
        editEventDate.setOnClickListener {
            showDatePicker(eventCalendar, editEventDate)
        }
        editEventTime.setOnClickListener {
            showTimePicker(eventCalendar, editEventTime)
        }

        //sales click listeners
        editSalesDate.setOnClickListener {
            showDatePicker(salesCalendar, editSalesDate)
        }
        editSalesTime.setOnClickListener {
            showTimePicker(salesCalendar, editSalesTime)
        }
    }

    private fun showDatePicker(targetCalender: Calendar, inputField: TextInputEditText){
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCalendar.timeInMillis = selection

            targetCalender.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
            targetCalender.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
            targetCalender.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            inputField.setText(formatter.format(targetCalender.time))
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker(targetCalender: Calendar, inputField: TextInputEditText){
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setTitleText("Select time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            targetCalender.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            targetCalender.set(Calendar.MINUTE, timePicker.minute)

            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            inputField.setText(formatter.format(targetCalender.time))
        }

        timePicker.show(parentFragmentManager, "TIME_PICKER")
    }

    //publish button
    private fun publishEvent(view: View) {
        val title = view.findViewById<TextInputEditText>(R.id.editTitle).text.toString().trim()
        val host = view.findViewById<TextInputEditText>(R.id.editHost).text.toString().trim()
        val description = view.findViewById<TextInputEditText>(R.id.editDescription).text.toString().trim()
        val location = view.findViewById<MaterialAutoCompleteTextView>(R.id.editLocation).text.toString().trim()
        val durationStr = view.findViewById<TextInputEditText>(R.id.editDuration).text.toString().trim()
        val capacityStr = view.findViewById<TextInputEditText>(R.id.editCapacity).text.toString().trim()
        val priceStr = view.findViewById<TextInputEditText>(R.id.editPrice).text.toString().trim()

        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTags)
        val selectedTags = mutableListOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as com.google.android.material.chip.Chip
            if (chip.isChecked){
                selectedTags.add(chip.text.toString())
            }
        }

        //validation
        if (title.isEmpty()||location.isEmpty()||priceStr.isEmpty()||selectedImageUri == null){
            Toast.makeText(requireContext(), "Please fill in all required fields and select an image.", Toast.LENGTH_SHORT).show()
            return
        }

        //to numbers
        val duration = durationStr.toIntOrNull() ?: 0
        val capacity = capacityStr.toIntOrNull() ?: 0
        val price = priceStr.toDoubleOrNull() ?: 0.0

        Toast.makeText(requireContext(), "Uploading event...", Toast.LENGTH_SHORT).show()

        //image to url
        val fileName = UUID.randomUUID().toString() + ".jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child("EventImages/$fileName")

        storageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveDataToFirestore(
                        title, host, description, location, duration, capacity, price,
                        selectedTags, downloadUrl.toString()
                    )
                }
            }
            .addOnFailureListener{ e->
                Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDataToFirestore(
        title: String, host: String, description: String, location: String,
        duration: Int, capacity: Int, price: Double, tags: List<String>, imageUrl: String
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val eventMap = hashMapOf(
            "title" to title,
            "description" to description,
            "creatorId" to currentUserId,
            "host" to host,
            "dateAndTime" to eventCalendar.time,
            "duration" to "$duration hours",
            "ticketPrice" to price,
            "tags" to tags,
            "locationName" to location,
            "locationCoordinates" to GeoPoint(eventLatitude, eventLongitude),
            "coverImageUrl" to imageUrl,
            "liveAttendanceCount" to 0,
            "totalCapacity" to capacity,
            "ticketsAvailable" to capacity,
            "salesEndDateTime" to salesCalendar.time
        )

        FirebaseFirestore.getInstance().collection("events")
            .add(eventMap)
            .addOnSuccessListener { documentReference ->

                documentReference.update("eventId", documentReference.id)

                Toast.makeText(requireContext(), "Event successfully published!", Toast.LENGTH_SHORT).show()

                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to save event: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}