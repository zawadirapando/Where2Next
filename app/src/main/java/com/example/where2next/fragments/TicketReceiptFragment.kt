package com.example.where2next.fragments

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.where2next.R
import com.example.where2next.adapters.TicketAdapter
import com.example.where2next.models.Event
import com.example.where2next.models.Ticket
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class TicketReceiptFragment : Fragment() {

    private lateinit var viewPagerTickets: ViewPager2
    private lateinit var tabLayoutDots: TabLayout
    private lateinit var buttonBackToHome: Button

    // Progress UI Elements
    private lateinit var progressTicketReceipt: ProgressBar
    private lateinit var contentTicketReceipt: NestedScrollView

    private val db = FirebaseFirestore.getInstance()
    private var currentBuyerName: String = "Ticket Holder"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ticket_receipt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val event: Event? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("SELECTED_EVENT", Event::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("SELECTED_EVENT")
        }

        val ticket: Ticket? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("PURCHASED_TICKET", Ticket::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("PURCHASED_TICKET")
        }

        viewPagerTickets = view.findViewById(R.id.viewPagerTickets)
        tabLayoutDots = view.findViewById(R.id.tabLayoutTicketDots)
        buttonBackToHome = view.findViewById(R.id.buttonBackToHome)

        // Initialize Progress UI Elements
        progressTicketReceipt = view.findViewById(R.id.progressTicketReceipt)
        contentTicketReceipt = view.findViewById(R.id.contentTicketReceipt)

        val btnMenuEventDetails = view.findViewById<TextView>(R.id.btnMenuEventDetails)
        val btnMenuOrderDetails = view.findViewById<TextView>(R.id.btnMenuOrderDetails)
        val btnMenuDownloadTicket = view.findViewById<TextView>(R.id.btnMenuDownloadTicket)

        // 1. Event Details Button
        btnMenuEventDetails.setOnClickListener {
            if (event != null) {
                val bundle = Bundle()
                bundle.putParcelable("SELECTED_EVENT", event)
                val detailsFragment = EventDetailsFragment()
                detailsFragment.arguments = bundle
                parentFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, detailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        // 2. Order Details Button
        btnMenuOrderDetails.setOnClickListener {
            if (event != null && ticket != null) {
                val bundle = Bundle()
                bundle.putParcelable("SELECTED_EVENT", event)
                bundle.putParcelable("PURCHASED_TICKET", ticket)
                val orderDetailsFragment = OrderDetailsFragment()
                orderDetailsFragment.arguments = bundle
                parentFragmentManager.beginTransaction()
                    .replace(R.id.frameLayout, orderDetailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        // 3. Download Button
        btnMenuDownloadTicket.setOnClickListener {
            if (event != null && ticket != null) {
                generateAndDownloadPdf(event, ticket, currentBuyerName)
            }
        }

        if (event != null && ticket != null) {
            fetchBuyerNameAndSetupUI(ticket, event)
        } else {
            Toast.makeText(requireContext(), "Error loading ticket data", Toast.LENGTH_SHORT).show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateToTickets()
                }
            })

        buttonBackToHome.setOnClickListener {
            navigateToHome()
        }
    }

    private fun generateAndDownloadPdf(event: Event, ticket: Ticket, buyerName: String) {
        Toast.makeText(requireContext(), "Generating PDF...", Toast.LENGTH_SHORT).show()

        val pageWidth = 419
        val pageHeight = 595

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
        val dateString = event.dateAndTime?.let { formatter.format(it) } ?: "No date yet"

        // ── Colours ──────────────────────────────────────────────
        val bgColor     = Color.parseColor("#E87722") // orange — swap to any hex you like
        val white       = Color.WHITE
        val black       = Color.BLACK
        val whiteAlpha  = Color.argb(150, 255, 255, 255)

        // ── Paints ───────────────────────────────────────────────
        val bgPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val whitePaint = Paint().apply { color = white; style = Paint.Style.FILL }
        val textPaintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 11f; alpha = 160
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 14f; isFakeBoldText = true
        }
        val monoSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black; textSize = 9f; textAlign = Paint.Align.CENTER; alpha = 140
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val margin = 28f
        val tearY = pageHeight * 0.58f  // tear line sits at 58% down

        // ── Background ───────────────────────────────────────────
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // Rounded white card behind top section
        val topCard = android.graphics.RectF(margin, margin, pageWidth - margin, tearY - 8f)
        val cardPaint = Paint().apply { color = white; style = Paint.Style.FILL }
        canvas.drawRoundRect(topCard, 16f, 16f, cardPaint)

        // Rounded white card behind bottom section
        val bottomCard = android.graphics.RectF(margin, tearY + 8f, pageWidth - margin, pageHeight - margin)
        canvas.drawRoundRect(bottomCard, 16f, 16f, cardPaint)

        // ── Top section content ───────────────────────────────────
        val cardInnerLeft = margin + 16f
        var y = margin + 36f

        // Event title (centered in top card)
        textPaintTitle.textAlign = Paint.Align.CENTER
        canvas.drawText(event.title, pageWidth / 2f, y, textPaintTitle)
        y += 6f

        // Thin divider
        val divPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        canvas.drawLine(cardInnerLeft, y + 10f, pageWidth - cardInnerLeft, y + 10f, divPaint)
        y += 26f

        // Helper to draw a label + value pair
        fun drawField(label: String, value: String) {
            canvas.drawText(label, cardInnerLeft, y, labelPaint)
            y += 18f
            canvas.drawText(value, cardInnerLeft, y, valuePaint)
            y += 24f
        }

        drawField("ATTENDEE", buyerName)
        drawField("DATE AND TIME", dateString)
        drawField("LOCATION", event.locationName ?: "—")
        drawField("ORGANIZED BY", event.host ?: "—")

        // ── Dotted tear line ─────────────────────────────────────
        val dotPaint = Paint().apply {
            color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        canvas.drawLine(margin + 4f, tearY, pageWidth - margin - 4f, tearY, dotPaint)

        // ── Bottom section: QR code ───────────────────────────────
        val qrBitmap = generateQrCode(ticket.ticketId)
        if (qrBitmap != null) {
            val qrSize = 150
            val qrLeft = (pageWidth - qrSize) / 2f
            val qrTop  = tearY + 20f
            val dest = android.graphics.RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
            // White background behind QR
            val qrBg = android.graphics.RectF(qrLeft - 6f, qrTop - 6f, qrLeft + qrSize + 6f, qrTop + qrSize + 6f)
            canvas.drawRect(qrBg, whitePaint)
            canvas.drawBitmap(qrBitmap, null, dest, null)

            // Ticket ID below QR
            canvas.drawText(ticket.ticketId, pageWidth / 2f, qrTop + qrSize + 20f, monoSmall)
        }

        document.finishPage(page)

        // ── Save ─────────────────────────────────────────────────
        val fileName = "Ticket_${event.title.replace(" ", "_")}.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1) // ✅ mark pending while writing
            }
        }

        val resolver = requireContext().contentResolver
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val file = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName
            )
            android.net.Uri.fromFile(file)
        }

        try {
            uri?.let {
                resolver.openOutputStream(it)?.use { stream ->
                    document.writeTo(stream)
                }

                // ✅ Mark file as no longer pending (makes it visible to other apps)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }

                // ✅ Post a proper system download notification with open-on-tap
                val notificationManager = requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager

                val channelId = "ticket_downloads"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "Ticket Downloads",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Notifications for downloaded tickets" }
                    notificationManager.createNotificationChannel(channel)
                }

                // Intent that opens the PDF when the notification is tapped
                val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(it, "application/pdf")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    requireContext(),
                    0,
                    openIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notification = androidx.core.app.NotificationCompat.Builder(requireContext(), channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done) // ✅ checkmark arrow icon
                    .setContentTitle("Ticket Downloaded")
                    .setContentText("${event.title} — tap to open")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true) // dismisses when tapped
                    .build()

                notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun navigateToTickets() {
        parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, TicketsFragment())
            .commit()
    }

    private fun navigateToHome() {
        parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, HomeFragment())
            .commit()
    }

    private fun fetchBuyerNameAndSetupUI(ticket: Ticket, event: Event) {
        db.collection("users").document(ticket.userId).get()
            .addOnSuccessListener { document ->
                val firstName = document.getString("firstName") ?: ""
                val lastName = document.getString("lastName") ?: ""
                val combinedName = "$firstName $lastName".trim()
                currentBuyerName = if (combinedName.isNotEmpty()) combinedName else "Ticket Holder"

                // Hide loader and show content
                progressTicketReceipt.visibility = View.GONE
                contentTicketReceipt.visibility = View.VISIBLE

                setupCarousel(ticket, event, currentBuyerName)
            }
            .addOnFailureListener {
                currentBuyerName = "Ticket Holder"

                // Hide loader and show content even on failure
                progressTicketReceipt.visibility = View.GONE
                contentTicketReceipt.visibility = View.VISIBLE

                setupCarousel(ticket, event, currentBuyerName)
            }
    }

    private fun setupCarousel(ticket: Ticket, event: Event, buyerName: String) {
        val adapter = TicketAdapter(event, ticket, buyerName)
        viewPagerTickets.adapter = adapter
        TabLayoutMediator(tabLayoutDots, viewPagerTickets) { _, _ -> }.attach()
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