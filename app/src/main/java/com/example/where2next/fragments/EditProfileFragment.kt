package com.example.where2next.fragments

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.where2next.R
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var placesClient: PlacesClient
    private lateinit var locationAdapter: ArrayAdapter<String>
    private val locationNamesList = mutableListOf<String>()

    private val placeIdMap = mutableMapOf<String, String>()

    private lateinit var editFirstName: TextInputEditText
    private lateinit var editLastName: TextInputEditText
    private lateinit var editPhoneNumber: TextInputEditText
    private lateinit var editLocation: MaterialAutoCompleteTextView
    private lateinit var buttonSaveChanges: MaterialButton
    private lateinit var imageEditProfile: ImageView

    private var originalFname = ""
    private var originalLname = ""
    private var originalPhone = ""
    private var originalLocation = ""

    //image tracking
    private var selectedImageUri: Uri? = null
    private var isImageChanged = false
    private var isImageDeleted = false

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
        if (uri != null){
            selectedImageUri =uri
            isImageChanged = true

            imageEditProfile.visibility = View.VISIBLE
            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .into(imageEditProfile)

            checkForChanges()
        }
    }

    //google places
    private val autoCompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null){
            val place = Autocomplete.getPlaceFromIntent(result.data!!)

            editLocation.setText(place.name)
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR){
            val status = Autocomplete.getStatusFromIntent(result.data!!)
            Toast.makeText(requireContext(), "Error: ${status.statusMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editFirstName = view.findViewById(R.id.editFirstName)
        editLastName = view.findViewById(R.id.editLastName)
        editPhoneNumber = view.findViewById(R.id.editPhoneNumber)
        editLocation = view.findViewById(R.id.editLocation)
        buttonSaveChanges = view.findViewById(R.id.buttonSaveChanges)
        imageEditProfile = view.findViewById(R.id.imageEditProfile)

        //google
        if (!Places.isInitialized()){
            Places.initialize(requireContext(), com.example.where2next.BuildConfig.MAPS_API_KEY)
        }

        placesClient = Places.createClient(requireContext())

        locationAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            locationNamesList
        )
        editLocation.setAdapter(locationAdapter)

        //keystrokes
        editLocation.addTextChangedListener(object : TextWatcher {
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
                            editLocation.setAdapter(newAdapter)

                            editLocation.showDropDown()
                        }
                        .addOnFailureListener{ e->
                            Toast.makeText(requireContext(), "Google Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        })

        editLocation.setOnItemClickListener { parent, view, position, id ->
            val selectedCityName = locationNamesList[position]

            editLocation.setText(selectedCityName)

            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        //buttons
        view.findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.cardEditAvatar).setOnClickListener {
            showImageOptionsDialog()
        }

        view.findViewById<LinearLayout>(R.id.buttonChangePassword).setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, ChangePasswordFragment())
                .addToBackStack(null)
                .commit()
        }

        buttonSaveChanges.setOnClickListener{
            startSaveProcess()
        }

        fetchUserData(view)
        setupTextWatchers()
    }

    private fun showImageOptionsDialog(){
        val options = arrayOf("Choose from Gallery", "Take Photo", "Remove Photo", "Cancel")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> openCamera()
                    2 -> removePhoto()
                    3 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun removePhoto() {
        selectedImageUri = null
        isImageChanged = true
        isImageDeleted = true

        imageEditProfile.visibility = View.GONE
        checkForChanges()
    }

    private fun openCamera() {
        Toast.makeText(requireContext(), "Camera setup coming next!", Toast.LENGTH_SHORT).show()
    }

    private fun fetchUserData(view: View) {
        val currentUser = auth.currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document !=null && document.exists()) {
                    originalFname = document.getString("firstName") ?: ""
                    originalLname = document.getString("lastName") ?: ""
                    originalPhone = document.getString("phone") ?: ""
                    originalLocation = document.getString("location") ?: ""
                    val profileImageUrl = document.getString("profileImageUrl") ?: ""

                    editFirstName.setText(originalFname)
                    editLastName.setText(originalLname)
                    editPhoneNumber.setText(originalPhone)
                    editLocation.setText(originalLocation)

                    val textInitials = view.findViewById<TextView>(R.id.textEditInitials)
                    val imageProfile = view.findViewById<ImageView>(R.id.imageEditProfile)

                    val firstI = originalFname.firstOrNull()?.uppercaseChar() ?: ""
                    val lastI = originalLname.firstOrNull()?.uppercaseChar() ?: ""
                    textInitials.text = "$firstI$lastI"

                    if(profileImageUrl.isNotEmpty()) {
                        imageProfile.visibility = View.VISIBLE
                        Glide.with(requireContext())
                            .load(profileImageUrl)
                            .into(imageProfile)
                    }
                }
            }
            .addOnFailureListener{
                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkForChanges()
            }
        }

        editFirstName.addTextChangedListener(textWatcher)
        editLastName.addTextChangedListener(textWatcher)
        editPhoneNumber.addTextChangedListener(textWatcher)
        editLocation.addTextChangedListener(textWatcher)
    }

    private fun checkForChanges() {
        val currentFirst = editFirstName.text.toString().trim()
        val currentLast = editLastName.text.toString().trim()
        val currentPhone = editPhoneNumber.text.toString().trim()
        val currentLocation = editLocation.text.toString().trim()

        val hasChanged = currentFirst != originalFname ||
                         currentLast != originalLname ||
                         currentPhone != originalPhone ||
                         currentLocation != originalLocation ||
                         isImageChanged

        buttonSaveChanges.isEnabled = hasChanged
    }

    private fun startSaveProcess(){
        buttonSaveChanges.isEnabled = false
        buttonSaveChanges.text = "Saving..."

        if (isImageChanged && selectedImageUri != null){
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val storageRef = FirebaseStorage.getInstance().reference.child("ProfileImages/&fileName")

            storageRef.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        updateFirestore(downloadUrl.toString())
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                    buttonSaveChanges.isEnabled = true
                    buttonSaveChanges.text = "Save Changes"
                }
        }else if (isImageDeleted){
            updateFirestore("")
        }else {
            updateFirestore(null)
        }

    }

    private fun updateFirestore(newImageUrl: String?) {
        val currentUser = auth.currentUser ?: return

        val updates = hashMapOf<String, Any>(
            "firstName" to editFirstName.text.toString().trim(),
            "lastName" to editLastName.text.toString().trim(),
            "phone" to editPhoneNumber.text.toString().trim(),
            "location" to editLocation.text.toString().trim()
        )

        if(newImageUrl != null){
            updates["profileImageUrl"] = newImageUrl
        }
        db.collection("users").document(currentUser.uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                buttonSaveChanges.isEnabled = true
                buttonSaveChanges.text = "Save Changes"
            }
    }

}