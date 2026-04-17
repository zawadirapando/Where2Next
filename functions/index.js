const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();
const db = admin.firestore();

const CONSUMER_KEY = process.env.CONSUMER_KEY;
const CONSUMER_SECRET = process.env.CONSUMER_SECRET;
const SHORTCODE = "174379"; 
const PASSKEY = process.env.PASSKEY;

// Your live Cloud Function URL
const CALLBACK_URL = "https://us-central1-where2next-2bd2e.cloudfunctions.net/mpesaCallback";

// Helper function to get Daraja token
async function getAuthToken() {
    const auth = Buffer.from(`${CONSUMER_KEY}:${CONSUMER_SECRET}`).toString("base64");
    const response = await axios.get(
        "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials",
        { headers: { Authorization: `Basic ${auth}` } }
    );
    return response.data.access_token;
}

// 1. THE STK PUSH FUNCTION
exports.initiateStkPush = functions.https.onCall(async (request) => {
    // Extract payload safely
    const payload = request.data;
    const { phoneNumber, amount, eventId, userId } = payload;

    // Safety check
    if (!phoneNumber) {
        console.error("Invalid Payload:", payload);
        throw new functions.https.HttpsError("invalid-argument", "Phone number is missing!");
    }

    // Format phone to 254...
    const formattedPhone = phoneNumber.startsWith("0")
        ? `254${phoneNumber.substring(1)}`
        : phoneNumber;

    try {
        const token = await getAuthToken();

        // Generate Timestamp and Password
        const timestamp = new Date().toISOString().replace(/[^0-9]/g, "").slice(0, -3);
        const password = Buffer.from(`${SHORTCODE}${PASSKEY}${timestamp}`).toString("base64");

        const pushData = {
            BusinessShortCode: SHORTCODE,
            Password: password,
            Timestamp: timestamp,
            TransactionType: "CustomerPayBillOnline",
            Amount: Math.round(Number(amount)), // Must be a whole number!
            PartyA: formattedPhone,
            PartyB: SHORTCODE,
            PhoneNumber: formattedPhone,
            CallBackURL: CALLBACK_URL,
            AccountReference: "Where2Next Tickets",
            TransactionDesc: `Tickets for ${eventId}`
        };

        const response = await axios.post(
            "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest",
            pushData,
            { headers: { Authorization: `Bearer ${token}` } }
        );

        const checkoutId = response.data.CheckoutRequestID;

        // Save to database as PENDING
        await db.collection("transactions").doc(checkoutId).set({
            status: "PENDING",
            userId: userId,
            eventId: eventId,
            amount: amount,
            timestamp: admin.firestore.FieldValue.serverTimestamp()
        });

        return { success: true, checkoutRequestId: checkoutId };

    } catch (error) {
        console.error("STK Push Error:", error);
        throw new functions.https.HttpsError("internal", "Payment failed to initiate");
    }
});

// 2. THE CALLBACK FUNCTION
exports.mpesaCallback = functions.https.onRequest(async (req, res) => {
    // TRACKER: Print Safaricom's exact message to the logs
    console.log("M-Pesa Webhook Hit! Payload:", JSON.stringify(req.body));

    try {
        const callbackData = req.body.Body.stkCallback;
        const checkoutId = callbackData.CheckoutRequestID;
        const resultCode = callbackData.ResultCode;

        if (resultCode === 0) {
            // PAYMENT SUCCESSFUL
            const transactionRef = db.collection("transactions").doc(checkoutId);
            const transactionDoc = await transactionRef.get();
            const tData = transactionDoc.data();

            const ticketRef = db.collection("tickets").doc();
            await ticketRef.set({
                ticketId: ticketRef.id,
                eventId: tData.eventId,
                userId: tData.userId,
                quantity: 1,
                purchaseDate: admin.firestore.FieldValue.serverTimestamp()
            });

            await db.collection("events").doc(tData.eventId).update({
                ticketsAvailable: admin.firestore.FieldValue.increment(-1)
            });

            await transactionRef.update({
                status: "SUCCESS",
                ticketId: ticketRef.id
            });

        } else {
            // PAYMENT FAILED or CANCELLED
            await db.collection("transactions").doc(checkoutId).update({ status: "FAILED" });
        }

        res.status(200).send("Callback received");

    } catch (error) {
        // Catch any crash caused by Safaricom sending weird data
        console.error("Callback crashed!", error);
        res.status(500).send("Internal Server Error");
    }
});