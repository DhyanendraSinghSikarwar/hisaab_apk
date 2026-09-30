package com.hisaab.parser.corpus

object KotakCorpus {
    private const val SMS = "VM-KOTAKB"
    private const val MAIL = "Kotak Mahindra Bank <BankAlerts@kotak.com>"

    val samples = listOf(
        sms(SMS, "Sent Rs.300.00 from Kotak Bank AC X1234 to swiggy@icici on 25-09-26.UPI Ref 526812345678. Not you, https://kotak.com/KBANKT/Fraud",
            debit("300.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        sms(SMS, "Received Rs.5000.00 in your Kotak Bank AC X1234 from johndoe@okaxis on 20-09-26.UPI Ref:526312345678.",
            credit("5000.00", "1234", ref = "526312345678", date = "2026-09-20")),
        sms(SMS, "Rs.1,499.00 spent on Kotak Credit Card x9012 at AMAZON on 22/09/2026. Avl limit Rs.48,501.00. Not you? Call 18602662666",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "Your a/c XX1234 is credited by Rs.45,000.00 on 01-09-26 by NEFT from ACME CORP. Avl Bal Rs.57,340.50 -Kotak Bank",
            credit("45000.00", "1234", merchant = "Acme Corp", balance = "57340.50", date = "2026-09-01")),
        sms(SMS, "Rs.2000.00 withdrawn from Kotak Bank AC X1234 at ATM on 12-09-26. Avl bal Rs.8340.50",
            debit("2000.00", "1234", merchant = "ATM", balance = "8340.50", date = "2026-09-12")),
        sms(SMS, "Sent Rs.599.00 from Kotak Bank AC X1234 to netflix@hdfcbank on 15-09-26.UPI Ref 525812345678. Not you, https://kotak.com/KBANKT/Fraud",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", date = "2026-09-15")),
        sms(SMS, "Sent Rs.10000.00 from Kotak Bank AC X1234 to rahul.sharma@oksbi on 08-09-26.UPI Ref 525112345678. Not you, https://kotak.com/KBANKT/Fraud",
            debit("10000.00", "1234", ref = "525112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        sms(SMS, "Your Kotak Bank AC X1234 debited for Rs.2500.00 on 05-09-26 towards NACH-GROWW MF. Avl bal Rs.5840.50",
            invest("2500.00", "1234", merchant = "Groww", balance = "5840.50", date = "2026-09-05")),
        sms(SMS, "Payment of Rs.12,000.00 received towards your Kotak Credit Card x9012 on 19-09-26. Thank you.",
            transfer("12000.00", "9012", date = "2026-09-19")),
        sms(SMS, "Rs.349.00 spent on Kotak Debit Card x5678 at UBER on 20-09-26. Avl bal Rs.5491.50",
            debit("349.00", "5678", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(SMS, "Your OTP for Kotak Bank transaction of Rs.1499.00 at AMAZON is 572910. Valid for 10 minutes. Do not share."),
        sms(SMS, "Your transaction of Rs.500.00 at FLIPKART using Kotak Debit Card x5678 failed due to insufficient funds."),
        sms(SMS, "RAHUL SHARMA has requested Rs.500.00 from you on Kotak811. Pay only if you know the requester."),
        sms(SMS, "You are eligible for a Kotak 811 Super credit card with Rs.50,000 limit. Apply now: kotak.com/cc"),

        email(MAIL, "Dear Customer,\nWe wish to inform you that your account xx1234 is debited for Rs. 300.00 on 25-Sep-2026 towards UPI payment to swiggy@icici. UPI Reference: 526812345678.\nKotak Mahindra Bank",
            debit("300.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nYour account xx1234 is credited with Rs. 45,000.00 on 01-Sep-2026 by NEFT from ACME CORP. Your available balance is Rs. 57,340.50.",
            credit("45000.00", "1234", merchant = "Acme Corp", balance = "57340.50", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nYour Kotak Credit Card xx9012 has been used for Rs. 1,499.00 at AMAZON on 22-Sep-2026 18:10:05.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nRs. 2,000.00 has been withdrawn from your account xx1234 at ATM on 12-Sep-2026.",
            debit("2000.00", "1234", merchant = "ATM", date = "2026-09-12")),
        email(MAIL, "Dear Customer,\nYour account xx1234 is debited for Rs. 10,000.00 on 08-Sep-2026 towards IMPS to RAHUL SHARMA. IMPS Reference: 625112345678.",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        email(MAIL, "Dear Customer, your Kotak Credit Card xx9012 payment of Rs. 8,450.00 is due on 10-Oct-2026. Pay now to avoid late charges."),
    )
}
