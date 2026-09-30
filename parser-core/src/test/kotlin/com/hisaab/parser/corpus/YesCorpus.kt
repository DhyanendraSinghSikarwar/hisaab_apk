package com.hisaab.parser.corpus

object YesCorpus {
    private const val SMS = "VM-YESBNK"
    private const val MAIL = "YES BANK <alerts@yesbank.in>"

    val samples = listOf(
        sms(SMS, "INR 250.00 debited from YES BANK A/c XX1234 on 25-09-2026 for UPI to SWIGGY. RRN 526812345678. Avl Bal INR 12,090.50",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        sms(SMS, "INR 5,000.00 credited to YES BANK A/c XX1234 on 20-09-2026 from JOHN DOE. RRN 526312345678. Avl Bal INR 17,090.50",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", balance = "17090.50", date = "2026-09-20")),
        sms(SMS, "INR 1,499.00 spent on YES BANK Credit Card XX9012 at AMAZON on 22-09-2026. Avl Lmt INR 48,501.00",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "INR 45,000.00 credited to YES BANK A/c XX1234 on 01-09-2026 via NEFT from ACME CORP. UTR CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        sms(SMS, "INR 2,000.00 withdrawn at ATM from YES BANK A/c XX1234 on 12-09-2026. Avl Bal INR 60,090.50",
            debit("2000.00", "1234", merchant = "ATM", balance = "60090.50", date = "2026-09-12")),
        sms(SMS, "INR 599.00 debited from YES BANK A/c XX1234 on 15-09-2026 for UPI to NETFLIX. RRN 525812345678.",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", date = "2026-09-15")),
        sms(SMS, "INR 10,000.00 debited from YES BANK A/c XX1234 on 08-09-2026 for IMPS to RAHUL SHARMA. RRN 625112345678.",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        sms(SMS, "INR 3,000.00 debited from YES BANK A/c XX1234 on 05-09-2026 towards NACH-GROWW MUTUAL FUND. Avl Bal INR 46,491.50",
            invest("3000.00", "1234", merchant = "Groww", balance = "46491.50", date = "2026-09-05")),
        sms(SMS, "Payment of INR 10,000.00 received towards your YES BANK Credit Card XX9012 on 19-09-2026. Thank you.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        sms(SMS, "INR 349.00 spent on YES BANK Credit Card XX9012 at UBER on 20-09-2026. Avl Lmt INR 48,152.00",
            debit("349.00", "9012", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(SMS, "OTP is 381205 for your YES BANK Credit Card XX9012 transaction of INR 1,499.00 at AMAZON. Do not share."),
        sms(SMS, "Your txn of INR 500.00 on YES BANK Credit Card XX9012 at SWIGGY failed. Please retry."),
        sms(SMS, "Exclusive offer! Get up to 10% cashback on YES BANK Credit Card. Apply now: yesbank.in/cc"),

        email(MAIL, "Dear Customer,\nINR 250.00 has been debited from your YES BANK A/c XX1234 on 25-09-2026 for UPI to SWIGGY. RRN: 526812345678.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nINR 45,000.00 has been credited to your YES BANK A/c XX1234 on 01-09-2026 via NEFT from ACME CORP. UTR: CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nYour YES BANK Credit Card XX9012 has been used for INR 1,499.00 at AMAZON on 22-09-2026 18:10:05.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nINR 10,000.00 has been debited from your YES BANK A/c XX1234 on 08-09-2026 for IMPS to RAHUL SHARMA. RRN: 625112345678.",
            debit("10000.00", "1234", ref = "625112345678", merchant = "Rahul Sharma", date = "2026-09-08")),
        email(MAIL, "Dear Customer,\nPayment of INR 10,000.00 has been received towards your YES BANK Credit Card XX9012 on 19-09-2026.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        email(MAIL, "Dear Customer, your YES BANK Credit Card XX9012 statement is generated. Total Amount Due INR 6,200.00. Payment due date 08-10-2026."),
    )
}
