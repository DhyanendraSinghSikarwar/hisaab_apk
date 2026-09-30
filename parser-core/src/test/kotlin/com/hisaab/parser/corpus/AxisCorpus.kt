package com.hisaab.parser.corpus

object AxisCorpus {
    private const val SMS = "AX-AXISBK"
    private const val MAIL = "Axis Bank Alerts <alerts@axisbank.com>"

    val samples = listOf(
        sms(SMS, "INR 250.00 debited\nA/c no. XX1234\n25-09-26, 10:15:32\nUPI/P2M/526812345678/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        sms(SMS, "INR 5,000.00 credited\nA/c no. XX1234\n20-09-26, 09:02:11 IST\nUPI/P2A/526312345678/JOHN DOE\nAxis Bank",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", date = "2026-09-20")),
        sms("JM-AXISBK-S", "Spent\nCard no. XX5678\nINR 1,299.00\n22-09-26 18:44:02\nAMAZON\nAvl Lmt INR 98,701.00\nNot you? SMS BLOCK 5678 to 919951860002\nAxis Bank",
            debit("1299.00", "5678", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "INR 45,000.00 credited to A/c no. XX1234 on 01-09-26 at 11:20:05 IST. Info- NEFT/CITIN52026090112345/ACME CORP. Avl Bal INR 1,02,345.67 - Axis Bank",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "102345.67", date = "2026-09-01")),
        sms(SMS, "INR 2,000.00 debited\nA/c no. XX1234\n12-09-26, 14:35:10\nATM-WDL/AXIS MUMBAI/S1234567\nAxis Bank",
            debit("2000.00", "1234", merchant = "ATM", date = "2026-09-12")),
        sms(SMS, "INR 599.00 debited\nA/c no. XX1234\n15-09-26, 00:05:12\nUPI/P2M/525812345678/NETFLIX COM\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", date = "2026-09-15")),
        sms(SMS, "INR 10,000.00 debited\nA/c no. XX1234\n08-09-26, 12:00:45\nIMPS/P2A/625112345678/RAHUL SHARMA\nNot you? Call 18001035577\nAxis Bank",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        sms(SMS, "Payment of INR 20,000.00 has been received towards your Axis Bank Credit Card XX5678 on 19-09-26. Thank you.",
            transfer("20000.00", "5678", date = "2026-09-19")),
        sms("JM-AXISBK-S", "Spent\nCard no. XX5678\nINR 349.00\n20-09-26 22:01:45\nUBER INDIA\nAvl Lmt INR 98,352.00\nNot you? SMS BLOCK 5678 to 919951860002\nAxis Bank",
            debit("349.00", "5678", merchant = "Uber", date = "2026-09-20", kind = CARD)),
        sms(SMS, "INR 2,500.00 debited\nA/c no. XX1234\n05-09-26, 06:00:00\nACH-DR/GROWW MUTUAL FUND/1234567\nAxis Bank",
            invest("2500.00", "1234", merchant = "Groww", date = "2026-09-05")),

        sms(SMS, "OTP for txn of INR 1,299.00 at AMAZON on Axis Bank card XX5678 is 739104. Valid for 3 mins. Do not share. Axis Bank"),
        sms(SMS, "Txn of INR 750.00 on Axis Bank Card XX5678 at MYNTRA declined due to incorrect PIN. Axis Bank"),
        sms(SMS, "Pre-approved Axis Bank Credit Card with lifetime free benefits! Apply now: axisbk.com/cc T&C"),

        email(MAIL, "Dear Customer,\nINR 250.00 has been debited from your A/c no. XX1234 on 25-09-2026 at 10:15:32 IST. Transaction Info: UPI/P2M/526812345678/SWIGGY.\nIf this transaction was not initiated by you, please call 18001035577.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nINR 45,000.00 has been credited to your A/c no. XX1234 on 01-09-2026 at 11:20:05 IST. Transaction Info: NEFT/CITIN52026090112345/ACME CORP.\nAvailable balance: INR 1,02,345.67.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "102345.67", date = "2026-09-01")),
        email(MAIL, "Dear Cardholder,\nTransaction alert: INR 1,299.00 was spent on your Axis Bank Credit Card no. XX5678 at AMAZON on 22-09-2026 18:44:02 IST.\nAvailable limit: INR 98,701.00.",
            debit("1299.00", "5678", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nINR 10,000.00 has been debited from your A/c no. XX1234 on 08-09-2026 at 12:00:45 IST. Transaction Info: IMPS/P2A/625112345678/RAHUL SHARMA.",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        email(MAIL, "Dear Cardholder,\nPayment of INR 20,000.00 has been received towards your Axis Bank Credit Card XX5678 on 19-09-2026. Thank you.",
            transfer("20000.00", "5678", date = "2026-09-19")),
        email(MAIL, "Dear Cardholder, your Axis Bank Credit Card XX5678 bill is generated. Total Amount Due: INR 8,450.00 due by 10-10-2026. Pay now to avoid late fee."),
    )
}
