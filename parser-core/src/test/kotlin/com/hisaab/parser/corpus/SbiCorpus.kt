package com.hisaab.parser.corpus

object SbiCorpus {
    private const val UPI = "VM-SBIUPI"
    private const val CBS = "JD-CBSSBI"
    private const val CARD_SMS = "VM-SBICRD"
    private const val MAIL = "SBI <donotreply.sbiatm@alerts.sbi.co.in>"
    private const val CARD_MAIL = "SBI Card <onlinesbicard@sbicard.com>"

    val samples = listOf(
        sms(UPI, "Dear UPI user A/C X1234 debited by 250.0 on date 25Sep26 trf to SWIGGY Refno 526812345678. If not u? call 1800111109. -SBI",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        sms(UPI, "Dear SBI User, your A/c X1234-credited by Rs.5000 on 20Sep26 transfer from JOHN DOE Ref No 526312345678 -SBI",
            credit("5000.00", "1234", ref = "526312345678", merchant = "John Doe", date = "2026-09-20")),
        sms(CBS, "Dear Customer, Your A/C XXXXX1234 has a debit by transfer of Rs 1,500.00 on 18/09/26. Avl Bal Rs 12,340.50.-SBI",
            debit("1500.00", "1234", balance = "12340.50", date = "2026-09-18")),
        sms(CBS, "Dear Customer, Your A/C XXXXX1234 has a credit by NEFT of Rs 45,000.00 on 01/09/26 from ACME CORP. Avl Bal Rs 57,340.50.-SBI",
            credit("45000.00", "1234", merchant = "Acme Corp", balance = "57340.50", date = "2026-09-01")),
        sms(CBS, "Your A/C XXXXX1234 Debited INR 2,000.00 on 12/09/26 -Transferred to RAVI KUMAR. Avl Balance INR 10,340.50-SBI",
            debit("2000.00", "1234", merchant = "Ravi Kumar", balance = "10340.50", date = "2026-09-12")),
        sms(CARD_SMS, "Rs.1,249.00 spent on your SBI Credit Card ending 5678 at FLIPKART on 22/09/26. Trxn. not done by you? Report at sbicard.com",
            debit("1249.00", "5678", merchant = "Flipkart", date = "2026-09-22", kind = CARD)),
        sms("BZ-ATMSBI", "Rs 2,000.00 withdrawn at SBI ATM S1NB000123 from A/c X1234 on 12Sep26. Transaction number 626112345678. Avl Bal Rs 8,340.50. Not you? call 1800111109 -SBI",
            debit("2000.00", "1234", ref = "626112345678", merchant = "ATM", balance = "8340.50", date = "2026-09-12")),
        sms(UPI, "Dear UPI user A/C X1234 debited by 599.0 on date 15Sep26 trf to NETFLIX Refno 525812345678. If not u? call 1800111109. -SBI",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", date = "2026-09-15")),
        sms(UPI, "Dear UPI user A/C X1234 debited by 3,000.0 on date 05Sep26 trf to ZERODHA BROKING Refno 524812345678. If not u? call 1800111109. -SBI",
            invest("3000.00", "1234", ref = "524812345678", merchant = "Zerodha", date = "2026-09-05")),
        sms(UPI, "Dear SBI User, your A/c X1234-credited by Rs.1200 on 10Sep26 transfer from AMIT KUMAR Ref No 525312345678 -SBI",
            credit("1200.00", "1234", ref = "525312345678", merchant = "Amit Kumar", date = "2026-09-10")),

        sms(CARD_SMS, "OTP for online purchase of Rs. 1249.00 at FLIPKART thru SBI Card ending 5678 is 482913. Do not share it with anyone."),
        sms(CBS, "Dear Customer, your transaction of Rs 500.00 at AMAZON using SBI Debit Card X5678 has failed due to insufficient balance."),
        sms(CARD_SMS, "Get SBI Card SimplySAVE with 10X reward points! Apply now: sbicard.com/apply T&C"),

        email(MAIL, "Dear Customer,\nYour A/C XXXXX1234 has been debited by Rs. 250.00 on 25-09-2026 through UPI. UPI Ref No 526812345678. Beneficiary: SWIGGY. Available balance: Rs. 12,090.50.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nYour A/C XXXXX1234 has been credited by Rs. 45,000.00 on 01-09-2026 by NEFT from ACME CORP. Available balance: Rs. 57,340.50.",
            credit("45000.00", "1234", merchant = "Acme Corp", balance = "57340.50", date = "2026-09-01")),
        email(CARD_MAIL, "Dear Cardholder,\nThank you for using your SBI Credit Card ending 5678 for Rs. 1,249.00 at FLIPKART on 22/09/2026.",
            debit("1249.00", "5678", merchant = "Flipkart", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nRs. 2,000.00 has been withdrawn from your A/C XXXXX1234 at SBI ATM on 12-09-2026. Transaction number 626112345678. Available balance: Rs. 8,340.50.",
            debit("2000.00", "1234", ref = "626112345678", merchant = "ATM", balance = "8340.50", date = "2026-09-12")),
        email(CARD_MAIL, "Dear Cardholder,\nWe have received payment of Rs. 15,000.00 towards your SBI Credit Card ending 5678 on 19-09-2026. Thank you.",
            transfer("15000.00", "5678", date = "2026-09-19")),
        email(CARD_MAIL, "Dear Cardholder, Your SBI Credit Card statement for Sep 2026 is ready. Total Amount Due Rs. 8,450.00. Min Amount Due Rs. 450.00. Payment Due Date 12-Oct-2026."),
    )
}
