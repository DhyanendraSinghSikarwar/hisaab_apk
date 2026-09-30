package com.hisaab.parser.corpus

object IdfcCorpus {
    private const val SMS = "AD-IDFCFB"
    private const val MAIL = "IDFC FIRST Bank <transaction.alerts@idfcfirstbank.com>"

    val samples = listOf(
        sms(SMS, "Your A/C XXXXXXX1234 has been debited by INR 250.00 on 25/09/2026 for UPI txn to SWIGGY. UPI Ref 526812345678. Avl Bal INR 12,090.50 - IDFC FIRST Bank",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        sms(SMS, "Your A/C XXXXXXX1234 has been credited with INR 5,000.00 on 20/09/2026 from JOHN DOE via UPI. UPI Ref 526312345678. Avl Bal INR 17,090.50 - IDFC FIRST Bank",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", balance = "17090.50", date = "2026-09-20")),
        sms(SMS, "INR 1,499.00 spent on your IDFC FIRST Bank Credit Card ending XX9012 at AMAZON on 22/09/2026 at 06:40 PM. Avl Limit: INR 48,501.00",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "Your A/C XXXXXXX1234 has been credited with INR 45,000.00 on 01/09/2026 by NEFT from ACME CORP. Ref CITIN52026090112345. Avl Bal INR 62,090.50",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "62090.50", date = "2026-09-01")),
        sms(SMS, "INR 2,000.00 withdrawn from your A/C XXXXXXX1234 at ATM on 12/09/2026. Avl Bal INR 60,090.50 - IDFC FIRST Bank",
            debit("2000.00", "1234", merchant = "ATM", balance = "60090.50", date = "2026-09-12")),
        sms(SMS, "Your A/C XXXXXXX1234 has been debited by INR 599.00 on 15/09/2026 for UPI txn to NETFLIX. UPI Ref 525812345678. Avl Bal INR 59,491.50",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", balance = "59491.50", date = "2026-09-15")),
        sms(SMS, "Your A/C XXXXXXX1234 has been debited by INR 10,000.00 on 08/09/2026 for IMPS to RAHUL SHARMA. IMPS Ref 625112345678.",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        sms(SMS, "Your A/C XXXXXXX1234 has been debited by INR 5,000.00 on 05/09/2026 towards ACH-ZERODHA BROKING. Avl Bal INR 49,491.50",
            invest("5000.00", "1234", merchant = "Zerodha", balance = "49491.50", date = "2026-09-05")),
        sms(SMS, "Payment of INR 15,000.00 received towards your IDFC FIRST Bank Credit Card ending XX9012 on 19/09/2026. Thank you!",
            transfer("15000.00", "9012", date = "2026-09-19")),
        sms(SMS, "INR 349.00 spent on your IDFC FIRST Bank Credit Card ending XX9012 at UBER on 20/09/2026 at 10:01 PM. Avl Limit: INR 48,152.00",
            debit("349.00", "9012", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(SMS, "Your IDFC FIRST Bank OTP for transaction of INR 1,499.00 at AMAZON is 902114. Valid for 5 mins."),
        sms(SMS, "Your IDFC FIRST Bank Credit Card XX9012 payment of INR 8,450.00 is due on 10/10/2026. Pay now: idfcfirst.bank.in/pay"),
        sms(SMS, "Transaction of INR 999.00 at MYNTRA on IDFC FIRST Bank Credit Card XX9012 was declined. Call 1800 10 888."),
        sms(SMS, "Pre-approved FIRST Personal Loan of up to INR 10,00,000 at attractive rates. Apply now: idfcfirst.bank.in/pl"),

        email(MAIL, "Dear Customer,\nINR 250.00 has been debited from your account XXXXXXX1234 towards UPI payment to SWIGGY on 25-09-2026. UPI Ref: 526812345678.\nAvailable balance: INR 12,090.50",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nINR 5,000.00 has been credited to your account XXXXXXX1234 from JOHN DOE on 20-09-2026. UPI Ref: 526312345678.",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", date = "2026-09-20")),
        email(MAIL, "Dear Customer,\nINR 1,499.00 has been spent on your IDFC FIRST Bank Credit Card ending XX9012 at AMAZON on 22-09-2026 18:40:00.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nINR 45,000.00 has been credited to your account XXXXXXX1234 by NEFT from ACME CORP on 01-09-2026. UTR: CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nWe have received payment of INR 15,000.00 towards your IDFC FIRST Bank Credit Card ending XX9012 on 19-09-2026.",
            transfer("15000.00", "9012", date = "2026-09-19")),
        email(MAIL, "Dear Customer, you are eligible for a pre-approved loan of INR 5,00,000 from IDFC FIRST Bank. Apply now."),
    )
}
