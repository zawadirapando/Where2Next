package com.example.where2next.models

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.util.Date

// ==========================================
// 1. THE CUSTOM TRANSLATOR (PARCELER)
// ==========================================
object GeoPointParceler : Parceler<GeoPoint?> {

    // How to UNPACK it from the suitcase
    override fun create(parcel: Parcel): GeoPoint? {
        val isPresent = parcel.readInt() // Check if it was null
        return if (isPresent == 1) {
            // It's not null! Extract the two numbers and rebuild the GeoPoint
            val lat = parcel.readDouble()
            val lng = parcel.readDouble()
            GeoPoint(lat, lng)
        } else {
            null
        }
    }

    // How to PACK it into the suitcase
    override fun GeoPoint?.write(parcel: Parcel, flags: Int) {
        if (this != null) {
            parcel.writeInt(1) // Write a "1" to prove it exists
            parcel.writeDouble(this.latitude) // Pack the latitude
            parcel.writeDouble(this.longitude) // Pack the longitude
        } else {
            parcel.writeInt(0) // Write a "0" to say it's null, skip the math
        }
    }
}

@Parcelize
@TypeParceler<GeoPoint?, GeoPointParceler>() // <-- THE MAGIC LINE
data class Event (
    var eventId: String = "",
    var title: String = "",
    var description: String = "",
    var creatorId: String = "",
    var host: String = "",
    var dateAndTime: Date? = null,
    var duration: String = "",
    var ticketPrice: Double = 0.0,
    var tags: List<String> = emptyList(),
    var locationName: String = "",
    var locationCoordinates: GeoPoint? = null,
    var coverImageUrl: String = "",
    var liveAttendanceCount: Int = 0,
    var totalCapacity: Int = 0,
    var ticketsAvailable: Int = 0,
    var salesEndDateTime: Date? = null
) : Parcelable