const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();
const db = admin.firestore();

const CONSUMER_KEY = process.env.CONSUMER_KEY;
const CONSUMER_SECRET = process.env.CONSUMER_SECRET;
const SHORTCODE = "174379";
const PASSKEY = process.env.PASSKEY;

// ── Use the run.app URL shown in your Firebase console ──
const CALLBACK_URL = "https://us-central1-where2next-2bd2e.cloudfunctions.net/mpesaCallback";

async function getAuthToken() {
    const auth = Buffer.from(`${CONSUMER_KEY}:${CONSUMER_SECRET}`).toString("base64");
    const response = await axios.get(
        "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials",
        { headers: { Authorization: `Basic ${auth}` } }
    );
    return response.data.access_token;
}

exports.initiateStkPush = onCall(async (request) => {
    const payload = request.data;
    const { phoneNumber, amount, eventId, userId, quantity } = payload;

    if (!phoneNumber || !userId || !eventId) {
        throw new HttpsError("invalid-argument", "Missing required fields");
    }

    const formattedPhone = phoneNumber.startsWith("0")
        ? `254${phoneNumber.substring(1)}`
        : phoneNumber;

    try {
        // Idempotency check — now inside try-catch so index errors are caught
        const existingQuery = await db.collection("transactions")
            .where("userId", "==", userId)
            .where("eventId", "==", eventId)
            .where("status", "==", "PENDING")
            .get();

        if (!existingQuery.empty) {
            const existingDoc = existingQuery.docs[0];
            console.log("Returning existing PENDING transaction:", existingDoc.id);
            return { success: true, checkoutRequestId: existingDoc.id };
        }

        const token = await getAuthToken();

        const timestamp = new Date().toISOString().replace(/[^0-9]/g, "").slice(0, -3);
        const password = Buffer.from(`${SHORTCODE}${PASSKEY}${timestamp}`).toString("base64");

        const response = await axios.post(
            "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest",
            {
                BusinessShortCode: SHORTCODE,
                Password: password,
                Timestamp: timestamp,
                TransactionType: "CustomerPayBillOnline",
                Amount: Math.round(Number(amount)),
                PartyA: formattedPhone,
                PartyB: SHORTCODE,
                PhoneNumber: formattedPhone,
                CallBackURL: CALLBACK_URL,
                AccountReference: "Where2Next Tickets",
                TransactionDesc: `Tickets for ${eventId}`
            },
            { headers: { Authorization: `Bearer ${token}` } }
        );

        const checkoutId = response.data.CheckoutRequestID;

        await db.collection("transactions").doc(checkoutId).set({
            status: "PENDING",
            userId: userId,
            eventId: eventId,
            amount: amount,
            quantity: quantity ?? 1,
            timestamp: admin.firestore.FieldValue.serverTimestamp()
        });

        return { success: true, checkoutRequestId: checkoutId };

    } catch (error) {
        console.error("STK Push Error:", error); // This will now show the REAL error in logs
        throw new HttpsError("internal", error.message || "Payment failed to initiate");
    }
});

exports.mpesaCallback = onRequest(async (req, res) => {
    console.log("M-Pesa Webhook Hit:", JSON.stringify(req.body));

    try {
        const callbackData = req.body.Body.stkCallback;
        const checkoutId = callbackData.CheckoutRequestID;
        const resultCode = callbackData.ResultCode;

        if (resultCode === 0) {
            const transactionRef = db.collection("transactions").doc(checkoutId);

            await db.runTransaction(async (t) => {
                const transactionDoc = await t.get(transactionRef);

                if (!transactionDoc.exists) {
                    console.log("Transaction not found:", checkoutId);
                    return;
                }

                const tData = transactionDoc.data();

                if (tData.status === "SUCCESS") {
                    console.log("Duplicate callback ignored for:", checkoutId);
                    return;
                }

                const ticketRef = db.collection("tickets").doc(checkoutId);

                t.set(ticketRef, {
                    ticketId: checkoutId,
                    eventId: tData.eventId,
                    userId: tData.userId,
                    quantity: tData.quantity ?? 1,
                    totalPaid: tData.amount,
                    purchaseTimestamp: admin.firestore.FieldValue.serverTimestamp(),
                    isScanned: false,
                    qrPayload: checkoutId,
                    dynamicSeed: ""
                });

                t.update(db.collection("events").doc(tData.eventId), {
                    ticketsAvailable: admin.firestore.FieldValue.increment(-(tData.quantity ?? 1))
                });

                t.update(transactionRef, {
                    status: "SUCCESS",
                    ticketId: checkoutId
                });
            });

            try {
                const transactionDoc = await transactionRef.get();
                const tData = transactionDoc.data();

                if (tData?.userId) {
                    const userDoc = await db.collection("users").doc(tData.userId).get();
                    const fcmToken = userDoc.data()?.fcmToken;

                    if (fcmToken) {
                        await admin.messaging().send({
                            notification: {
                                title: "Payment Confirmed!",
                                body: "Your M-Pesa payment was successful. Your ticket is now in your wallet."
                            },
                            token: fcmToken
                        });
                        console.log("Notification sent to user:", tData.userId);
                    }
                }
            } catch (notifError) {
                console.error("Notification failed (non-critical):", notifError);
            }

        } else {
            await db.collection("transactions").doc(checkoutId).update({ status: "FAILED" });
        }

        res.status(200).send("Callback received");

    } catch (error) {
        console.error("Callback crashed:", error);
        res.status(200).send("Callback received");
    }
});

exports.notifyNewEvent = onDocumentCreated("events/{eventId}", async (event) => {
    const eventData = event.data.data();
    const eventName = eventData.title || "A new event";
    const tags = eventData.tags;
    const eventId = event.params.eventId; // <- get the eventId from the path

    if (!tags || tags.length === 0) return null;

    const promises = tags.map(tag =>
        admin.messaging().send({
            notification: {
                title: "New Event Match!",
                body: `${eventName} is happening soon. Grab your tickets!`
            },
            data: {
                eventId: eventId  // <- send eventId as data payload
            },
            android: {
                notification: {
                    click_action: "OPEN_HOME"
                }
            },
            topic: tag
        })
    );

    return Promise.all(promises);
});