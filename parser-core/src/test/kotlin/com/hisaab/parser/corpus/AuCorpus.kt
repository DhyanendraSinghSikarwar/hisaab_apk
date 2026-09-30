package com.hisaab.parser.corpus

object AuCorpus {
    private const val SMS = "VM-AUBANK"
    private const val MAIL = "AU Small Finance Bank <noreply@aubank.in>"

    val samples = listOf(
        sms(SMS, "INR 250.00 debited from AU Bank A/c XX1234 on 25-Sep-26 via UPI to SWIGGY. Ref 526812345678. Bal INR 12,090.50",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        sms(SMS, "INR 5,000.00 credited to AU Bank A/c XX1234 on 20-Sep-26 via UPI from JOHN DOE. Ref 526312345678. Bal INR 17,090.50",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", balance = "17090.50", date = "2026-09-20")),
        sms(SMS, "INR 1,499.00 spent on AU Bank Credit Card XX9012 at AMAZON on 22-Sep-26. Avl Lmt INR 48,501.00",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "INR 45,000.00 credited to AU Bank A/c XX1234 on 01-Sep-26 via NEFT from ACME CORP. UTR CITIN52026090112345. Bal INR 62,090.50",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "62090.50", date = "2026-09-01")),
        sms(SMS, "INR 2,000.00 withdrawn from AU Bank A/c XX1234 at ATM on 12-Sep-26. Bal INR 60,090.50",
            debit("2000.00", "1234", merchant = "ATM", balance = "60090.50", date = "2026-09-12")),
        sms(SMS, "INR 599.00 debited from AU Bank A/c XX1234 on 15-Sep-26 via UPI to NETFLIX. Ref 525812345678. Bal INR 59,491.50",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", balance = "59491.50", date = "2026-09-15")),
        sms(SMS, "INR 10,000.00 debited from AU Bank A/c XX1234 on 08-Sep-26 via IMPS to RAHUL SHARMA. Ref 625112345678. Bal INR 49,491.50",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", balance = "49491.50", date = "2026-09-08")),
        sms(SMS, "INR 5,000.00 debited from AU Bank A/c XX1234 on 05-Sep-26 towards NACH-ZERODHA BROKING. Bal INR 44,491.50",
            invest("5000.00", "1234", merchant = "Zerodha", balance = "44491.50", date = "2026-09-05")),
        sms(SMS, "Payment of INR 10,000.00 received towards your AU Bank Credit Card XX9012 on 19-Sep-26. Thank you.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        sms(SMS, "INR 349.00 spent on AU Bank Credit Card XX9012 at UBER on 20-Sep-26. Avl Lmt INR 48,152.00",
            debit("349.00", "9012", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(SMS, "Use OTP 553901 to complete your AU Bank transaction of INR 1,499.00 at AMAZON. Do not share."),
        sms(SMS, "Transaction of INR 500.00 on AU Bank Credit Card XX9012 at MYNTRA was unsuccessful."),
        sms(SMS, "Limited period offer! Get up to 5% cashback with AU Bank LIT Credit Card. Apply now: aubank.in/lit"),

        email(MAIL, "Dear Customer,\nINR 250.00 has been debited from your AU Bank A/c XX1234 on 25-09-2026 via UPI to SWIGGY. Ref: 526812345678.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nINR 45,000.00 has been credited to your AU Bank A/c XX1234 on 01-09-2026 via NEFT from ACME CORP. UTR: CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nYour AU Bank Credit Card XX9012 has been used for INR 1,499.00 at AMAZON on 22-09-2026 18:10:05.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nINR 2,000.00 has been withdrawn from your AU Bank A/c XX1234 at ATM on 12-09-2026.",
            debit("2000.00", "1234", merchant = "ATM", date = "2026-09-12")),
        email(MAIL, "Dear Customer,\nPayment of INR 10,000.00 has been received towards your AU Bank Credit Card XX9012 on 19-09-2026.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        email(MAIL, "Dear Customer, your AU Bank Credit Card XX9012 statement is ready. Total Amount Due INR 6,200.00, due by 08-10-2026."),
    )
}
