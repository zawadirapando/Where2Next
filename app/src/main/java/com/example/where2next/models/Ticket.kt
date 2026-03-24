package com.example.where2next.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Ticket (
    var ticketId: String = "",
    var eventId: String = "",
    var userId: String = "",
    var quantity: Int = 1,
    var totalPaid: Double = 0.0,
    var purchaseTimestamp: Timestamp? = null,
    var qrPayload: String = "",
    var isScanned: Boolean = false,
    var dynamicSeed: String = ""
) : Parcelable