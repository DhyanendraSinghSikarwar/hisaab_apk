package com.hisaab.parser.corpus

object PnbCorpus {
    private const val SMS = "VM-PNBSMS"
    private const val MAIL = "PNB <alerts@pnb.co.in>"

    val samples = listOf(
        sms(SMS, "Your a/c XX1234 debited INR 250.00 on 25-09-26 by UPI Ref 526812345678 to SWIGGY. Avl Bal INR 12,090.50 -PNB",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        sms(SMS, "A/c XX1234 credited INR 5,000.00 on 20-09-26 by UPI Ref 526312345678 from JOHN DOE. Avl Bal INR 17,090.50 -PNB",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", balance = "17090.50", date = "2026-09-20")),
        sms(SMS, "INR 1,499.00 spent on PNB Credit Card XX9012 at AMAZON on 22-09-26. Avl Limit INR 48,501.00 -PNB",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "A/c XX1234 credited INR 45,000.00 on 01-09-26 by NEFT from ACME CORP. UTR CITIN52026090112345. Avl Bal INR 62,090.50 -PNB",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "62090.50", date = "2026-09-01")),
        sms(SMS, "A/c XX1234 debited INR 2,000.00 on 12-09-26 at ATM. Avl Bal INR 60,090.50 -PNB",
            debit("2000.00", "1234", merchant = "ATM", balance = "60090.50", date = "2026-09-12")),
        sms(SMS, "Your a/c XX1234 debited INR 599.00 on 15-09-26 by UPI Ref 525812345678 to NETFLIX. Avl Bal INR 59,491.50 -PNB",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", balance = "59491.50", date = "2026-09-15")),
        sms(SMS, "Your a/c XX1234 debited INR 10,000.00 on 08-09-26 by IMPS Ref 625112345678 to RAHUL SHARMA. Avl Bal INR 49,491.50 -PNB",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", balance = "49491.50", date = "2026-09-08")),
        sms(SMS, "Your a/c XX1234 debited INR 3,000.00 on 05-09-26 towards NACH-ICCL MUTUAL FUND. Avl Bal INR 46,491.50 -PNB",
            invest("3000.00", "1234", merchant = "ICCL", balance = "46491.50", date = "2026-09-05")),
        sms(SMS, "Payment of INR 10,000.00 received towards your PNB Credit Card XX9012 on 19-09-26. Thank you -PNB",
            transfer("10000.00", "9012", date = "2026-09-19")),
        sms(SMS, "INR 349.00 spent on PNB Credit Card XX9012 at UBER on 20-09-26. Avl Limit INR 48,152.00 -PNB",
            debit("349.00", "9012", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(SMS, "Dear Customer, OTP for your PNB transaction of INR 1,499.00 is 771204. Do not share with anyone -PNB"),
        sms(SMS, "Your transaction of INR 500.00 on PNB Debit Card XX5678 at AMAZON has been declined. -PNB"),
        sms(SMS, "You are eligible for a PNB pre-approved personal loan. Apply now at pnb.bank.in -PNB"),

        email(MAIL, "Dear Customer,\nYour account XX1234 has been debited with INR 250.00 on 25-09-2026 by UPI Ref 526812345678 to SWIGGY.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nYour account XX1234 has been credited with INR 45,000.00 on 01-09-2026 by NEFT from ACME CORP. UTR CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nYour PNB Credit Card XX9012 has been used for INR 1,499.00 at AMAZON on 22-09-2026 18:10:05.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nINR 2,000.00 has been withdrawn from your account XX1234 at ATM on 12-09-2026.",
            debit("2000.00", "1234", merchant = "ATM", date = "2026-09-12")),
        email(MAIL, "Dear Customer,\nPayment of INR 10,000.00 has been received towards your PNB Credit Card XX9012 on 19-09-2026.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        email(MAIL, "Dear Customer, your PNB Credit Card XX9012 bill of INR 6,200.00 is due on 08-10-2026. Pay now to avoid charges."),
    )
}
