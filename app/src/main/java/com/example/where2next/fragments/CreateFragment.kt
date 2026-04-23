package com.example.where2next.fragments

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.example.where2next.models.Event
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
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
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
    private var existingImageUrl: String? = null
    private var eventIdToEdit: String? = null

    private val eventCalendar = Calendar.getInstance()
    private val salesCalendar = Calendar.getInstance()

    // Track whether dates have been set by the user
    private var eventDateSet = false
    private var eventTimeSet = false
    private var salesDateSet = false
    private var salesTimeSet = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eventToEdit = arguments?.getParcelable<Event>("EDIT_EVENT")

        val categories = listOf("Live Music", "Nightlife", "Food And Drink", "Arts And Culture", "Sports", "Wellness", "Comedy", "Networking", "Markets", "Outdoor", "Film", "Family", "Fashion", "Gaming")
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCreate)

        for (category in categories) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                setChipBackgroundColorResource(R.color.chip_background_state)

                if (eventToEdit != null) {
                    val formattedTag = category.lowercase().replace(" ", "_")
                    isChecked = eventToEdit.tags?.contains(formattedTag) == true
                }
            }
            chipGroup.addView(chip)
        }

        if (eventToEdit != null) {
            eventIdToEdit = eventToEdit.eventId
            existingImageUrl = eventToEdit.coverImageUrl

            view.findViewById<EditText>(R.id.editTitle).setText(eventToEdit.title)
            view.findViewById<EditText>(R.id.editDescription).setText(eventToEdit.description)
            view.findViewById<EditText>(R.id.editHost).setText(eventToEdit.host)
            view.findViewById<EditText>(R.id.editLocation).setText(eventToEdit.locationName)

            val durationDigits = eventToEdit.duration?.replace(Regex("[^0-9]"), "") ?: ""
            view.findViewById<EditText>(R.id.editDuration).setText(durationDigits)

            view.findViewById<EditText>(R.id.editPrice).setText(eventToEdit.ticketPrice.toString())
            view.findViewById<EditText>(R.id.editCapacity).setText(eventToEdit.totalCapacity.toString())

            eventToEdit.locationCoordinates?.let { geoPoint ->
                eventLatitude = geoPoint.latitude
                eventLongitude = geoPoint.longitude
            }

            val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

            eventToEdit.dateAndTime?.let { date ->
                eventCalendar.time = date
                view.findViewById<TextInputEditText>(R.id.editEventDate).setText(dateFormatter.format(date))
                view.findViewById<TextInputEditText>(R.id.editEventTime).setText(timeFormatter.format(date))
                eventDateSet = true
                eventTimeSet = true
            }

            eventToEdit.salesEndDateTime?.let { date ->
                salesCalendar.time = date
                view.findViewById<TextInputEditText>(R.id.editSalesEndDate).setText(dateFormatter.format(date))
                view.findViewById<TextInputEditText>(R.id.editSalesEndTime).setText(timeFormatter.format(date))
                salesDateSet = true
                salesTimeSet = true
            }

            val imageView = view.findViewById<ImageView>(R.id.imageEventCover)
            if (!existingImageUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(existingImageUrl)
                    .centerCrop()
                    .into(imageView)
                imageView.imageTintList = null
            }

            view.findViewById<Button>(R.id.buttonPublish).text = "Update Event"
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

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val imageView = requireView().findViewById<ImageView>(R.id.imageEventCover)
            imageView.setImageURI(uri)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.imageTintList = null
        }
    }

    private fun setLocationDropdown(view: View) {
        val locationInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.editLocation)

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), com.example.where2next.BuildConfig.MAPS_API_KEY)
        }

        placesClient = Places.createClient(requireContext())

        locationAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            locationNamesList
        )
        locationInput.setAdapter(locationAdapter)

        locationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setCountries(listOf("KE"))
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
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Google Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        })

        locationInput.setOnItemClickListener { _, _, position, _ ->
            val selectedCityName = locationNamesList[position]
            val hiddenPlaceId = placeIdMap[selectedCityName]

            if (hiddenPlaceId != null) {
                val placeFields = listOf(Place.Field.LAT_LNG)
                val request = FetchPlaceRequest.newInstance(hiddenPlaceId, placeFields)

                placesClient.fetchPlace(request).addOnSuccessListener { response ->
                    if (response.place.latLng != null) {
                        eventLatitude = response.place.latLng!!.latitude
                        eventLongitude = response.place.latLng!!.longitude
                    }
                }
            }
        }
    }

    private fun setUpDateAndTimePickers(view: View) {
        val editEventDate = view.findViewById<TextInputEditText>(R.id.editEventDate)
        val editEventTime = view.findViewById<TextInputEditText>(R.id.editEventTime)
        val editSalesDate = view.findViewById<TextInputEditText>(R.id.editSalesEndDate)
        val editSalesTime = view.findViewById<TextInputEditText>(R.id.editSalesEndTime)

        editEventDate.setOnClickListener {
            showDatePicker(eventCalendar, editEventDate) { eventDateSet = true }
        }
        editEventTime.setOnClickListener {
            showTimePicker(eventCalendar, editEventTime) { eventTimeSet = true }
        }
        editSalesDate.setOnClickListener {
            showDatePicker(salesCalendar, editSalesDate) { salesDateSet = true }
        }
        editSalesTime.setOnClickListener {
            showTimePicker(salesCalendar, editSalesTime) { salesTimeSet = true }
        }
    }

    private fun showDatePicker(targetCalendar: Calendar, inputField: TextInputEditText, onSet: () -> Unit) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCalendar.timeInMillis = selection

            targetCalendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
            targetCalendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
            targetCalendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            inputField.setText(formatter.format(targetCalendar.time))
            onSet()
        }
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker(targetCalendar: Calendar, inputField: TextInputEditText, onSet: () -> Unit) {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setTitleText("Select time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            targetCalendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            targetCalendar.set(Calendar.MINUTE, timePicker.minute)

            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            inputField.setText(formatter.format(targetCalendar.time))
            onSet()
        }
        timePicker.show(parentFragmentManager, "TIME_PICKER")
    }

    private fun generateSearchTokens(title: String): List<String> {
        val tokens = mutableSetOf<String>()
        val lowerTitle = title.lowercase(Locale.getDefault())
        val cleanTitle = lowerTitle.replace(Regex("[^a-z0-9 ]"), "")

        for (i in 1..cleanTitle.length) {
            tokens.add(cleanTitle.substring(0, i))
        }

        val words = cleanTitle.split("\\s+".toRegex())
        for (word in words) {
            if (word.isNotBlank()) {
                for (i in 1..word.length) {
                    tokens.add(word.substring(0, i))
                }
            }
        }
        return tokens.toList()
    }

    private fun setLoadingState(view: View, isLoading: Boolean) {
        val buttonPublish = view.findViewById<Button>(R.id.buttonPublish)
        val buttonUploadImage = view.findViewById<Button>(R.id.buttonUploadImage)

        if (isLoading) {
            buttonPublish.isEnabled = false
            buttonPublish.text = if (eventIdToEdit != null) "Updating..." else "Uploading..."
            buttonUploadImage.isEnabled = false
        } else {
            buttonPublish.isEnabled = true
            buttonPublish.text = if (eventIdToEdit != null) "Update Event" else "Publish Event"
            buttonUploadImage.isEnabled = true
        }
    }

    private fun validateFields(view: View): Boolean {
        var isValid = true

        val titleLayout = view.findViewById<TextInputLayout>(R.id.layoutTitle)
        val hostLayout = view.findViewById<TextInputLayout>(R.id.layoutHost)
        val descriptionLayout = view.findViewById<TextInputLayout>(R.id.layoutDescription)
        val locationLayout = view.findViewById<TextInputLayout>(R.id.layoutLocation)
        val eventDateLayout = view.findViewById<TextInputLayout>(R.id.layoutEventDate)
        val eventTimeLayout = view.findViewById<TextInputLayout>(R.id.layoutEventTime)
        val salesDateLayout = view.findViewById<TextInputLayout>(R.id.layoutSalesEndDate)
        val salesTimeLayout = view.findViewById<TextInputLayout>(R.id.layoutSalesEndTime)
        val capacityLayout = view.findViewById<TextInputLayout>(R.id.layoutCapacity)
        val priceLayout = view.findViewById<TextInputLayout>(R.id.layoutPrice)

        val title = view.findViewById<TextInputEditText>(R.id.editTitle).text.toString().trim()
        val host = view.findViewById<TextInputEditText>(R.id.editHost).text.toString().trim()
        val description = view.findViewById<TextInputEditText>(R.id.editDescription).text.toString().trim()
        val location = view.findViewById<MaterialAutoCompleteTextView>(R.id.editLocation).text.toString().trim()
        val capacityStr = view.findViewById<TextInputEditText>(R.id.editCapacity).text.toString().trim()
        val priceStr = view.findViewById<TextInputEditText>(R.id.editPrice).text.toString().trim()

        // Clear previous errors
        titleLayout.error = null
        hostLayout.error = null
        descriptionLayout.error = null
        locationLayout.error = null
        eventDateLayout.error = null
        eventTimeLayout.error = null
        salesDateLayout.error = null
        salesTimeLayout.error = null
        capacityLayout.error = null
        priceLayout.error = null

        if (title.isEmpty()) {
            titleLayout.error = "Event title is required"
            isValid = false
        }

        if (host.isEmpty()) {
            hostLayout.error = "Host / organizer is required"
            isValid = false
        }

        if (description.isEmpty()) {
            descriptionLayout.error = "Description is required"
            isValid = false
        }

        if (location.isEmpty()) {
            locationLayout.error = "Location is required"
            isValid = false
        }

        if (!eventDateSet) {
            eventDateLayout.error = "Pick an event date"
            isValid = false
        }

        if (!eventTimeSet) {
            eventTimeLayout.error = "Pick an event time"
            isValid = false
        }

        if (!salesDateSet) {
            salesDateLayout.error = "Pick a sales end date"
            isValid = false
        }

        if (!salesTimeSet) {
            salesTimeLayout.error = "Pick a sales end time"
            isValid = false
        }

        if (capacityStr.isEmpty()) {
            capacityLayout.error = "Capacity is required"
            isValid = false
        } else if ((capacityStr.toIntOrNull() ?: 0) <= 0) {
            capacityLayout.error = "Enter a valid capacity"
            isValid = false
        }

        if (priceStr.isEmpty()) {
            priceLayout.error = "Price is required"
            isValid = false
        } else if ((priceStr.toDoubleOrNull() ?: -1.0) < 0) {
            priceLayout.error = "Enter a valid price"
            isValid = false
        }

        if (selectedImageUri == null && existingImageUrl.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please select a cover image", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCreate)
        val anyChecked = (0 until chipGroup.childCount).any { (chipGroup.getChildAt(it) as Chip).isChecked }
        if (!anyChecked) {
            Toast.makeText(requireContext(), "Please select at least one event tag", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun publishEvent(view: View) {
        if (!validateFields(view)) return

        val title = view.findViewById<TextInputEditText>(R.id.editTitle).text.toString().trim()
        val host = view.findViewById<TextInputEditText>(R.id.editHost).text.toString().trim()
        val description = view.findViewById<TextInputEditText>(R.id.editDescription).text.toString().trim()
        val location = view.findViewById<MaterialAutoCompleteTextView>(R.id.editLocation).text.toString().trim()
        val durationStr = view.findViewById<TextInputEditText>(R.id.editDuration).text.toString().trim()
        val capacityStr = view.findViewById<TextInputEditText>(R.id.editCapacity).text.toString().trim()
        val priceStr = view.findViewById<TextInputEditText>(R.id.editPrice).text.toString().trim()

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCreate)
        val selectedTags = mutableListOf<String>()

        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) {
                val topicName = chip.text.toString().lowercase().replace(" ", "_")
                FirebaseMessaging.getInstance().subscribeToTopic(topicName)
                selectedTags.add(topicName)
            }
        }

        val duration = durationStr.toIntOrNull() ?: 0
        val capacity = capacityStr.toIntOrNull() ?: 0
        val price = priceStr.toDoubleOrNull() ?: 0.0

        setLoadingState(view, true)

        if (selectedImageUri != null) {
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val storageRef = FirebaseStorage.getInstance().reference.child("EventImages/$fileName")

            storageRef.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        saveDataToFirestore(
                            view, title, host, description, location, duration, capacity, price,
                            selectedTags, downloadUrl.toString()
                        )
                    }
                }
                .addOnFailureListener { e ->
                    setLoadingState(view, false)
                    Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            saveDataToFirestore(
                view, title, host, description, location, duration, capacity, price,
                selectedTags, existingImageUrl!!
            )
        }
    }

    private fun saveDataToFirestore(
        view: View,
        title: String, host: String, description: String, location: String,
        duration: Int, capacity: Int, price: Double, tags: List<String>, imageUrl: String
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val db = FirebaseFirestore.getInstance()

        val eventRef = if (eventIdToEdit != null) {
            db.collection("events").document(eventIdToEdit!!)
        } else {
            db.collection("events").document()
        }

        val generatedSearchTokens = generateSearchTokens(title)
        val generatedSearchTitle = title.lowercase(Locale.getDefault())

        val eventMap = hashMapOf<String, Any>(
            "eventId" to eventRef.id,
            "title" to title,
            "searchTitle" to generatedSearchTitle,
            "searchTokens" to generatedSearchTokens,
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
            "totalCapacity" to capacity,
            "salesEndDateTime" to salesCalendar.time
        )

        if (eventIdToEdit == null) {
            eventMap["liveAttendanceCount"] = 0
            eventMap["ticketsAvailable"] = capacity
        }

        eventRef.set(eventMap, SetOptions.merge())
            .addOnSuccessListener {
                setLoadingState(view, false)
                Toast.makeText(requireContext(), if (eventIdToEdit != null) "Event updated!" else "Event published!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, HomeFragment())
                    .commit()
            }
            .addOnFailureListener { e ->
                setLoadingState(view, false)
                Toast.makeText(requireContext(), "Failed to save event: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}