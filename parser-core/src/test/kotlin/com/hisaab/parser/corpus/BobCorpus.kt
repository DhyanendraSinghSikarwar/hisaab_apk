package com.hisaab.parser.corpus

object BobCorpus {
    private const val SMS = "VM-BOBTXN"
    private const val CARD_SMS = "VM-BOBCRD"
    private const val MAIL = "Bank of Baroda <alerts@bankofbaroda.co.in>"
    private const val CARD_MAIL = "BOBCARD <alerts@bobcard.co.in>"

    val samples = listOf(
        sms(SMS, "Rs.250.00 Dr. from A/C XXXXXX1234 and Cr. to swiggy@icici. Ref:526812345678. AvlBal:Rs12090.50(2026:09:25 10:15:32). Not you? Call 18005700-BOB",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", balance = "12090.50", date = "2026-09-25")),
        sms(SMS, "Rs.5000.00 Cr. to A/C XXXXXX1234 from johndoe@okaxis. Ref:526312345678. AvlBal:Rs17090.50(2026:09:20 09:02:11) - BOB",
            credit("5000.00", "1234", ref = "526312345678", balance = "17090.50", date = "2026-09-20")),
        sms(CARD_SMS, "Dear BOBCARD User, Rs.1,499.00 spent on your BOBCARD ending 9012 at AMAZON on 22-09-2026. Avl limit Rs.48,501.00",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms("BZ-BOBSMS", "Dear Customer, Your A/c XXXXXX1234 is credited with Rs.45000.00 on 01-09-2026 by NEFT from ACME CORP. Avl Bal Rs.62090.50 -Bank of Baroda",
            credit("45000.00", "1234", merchant = "Acme Corp", balance = "62090.50", date = "2026-09-01")),
        sms(SMS, "Rs.2000.00 withdrawn from A/C XXXXXX1234 at ATM on 12-09-2026. AvlBal:Rs60090.50 - BOB",
            debit("2000.00", "1234", merchant = "ATM", balance = "60090.50", date = "2026-09-12")),
        sms(SMS, "Rs.599.00 Dr. from A/C XXXXXX1234 and Cr. to netflix@hdfcbank. Ref:525812345678. AvlBal:Rs59491.50(2026:09:15 00:05:12). Not you? Call 18005700-BOB",
            debit("599.00", "1234", ref = "525812345678", merchant = "Netflix", balance = "59491.50", date = "2026-09-15")),
        sms(SMS, "Rs.10000.00 Dr. from A/C XXXXXX1234 and Cr. to rahul.sharma@oksbi. Ref:525112345678. AvlBal:Rs49491.50(2026:09:08 12:00:45). Not you? Call 18005700-BOB",
            debit("10000.00", "1234", ref = "525112345678", merchant = "Rahul Sharma", balance = "49491.50", date = "2026-09-08")),
        sms("BZ-BOBSMS", "Dear Customer, Your A/c XXXXXX1234 is debited with Rs.2500.00 on 05-09-2026 towards NACH-ZERODHA BROKING. Avl Bal Rs.46991.50 -Bank of Baroda",
            invest("2500.00", "1234", merchant = "Zerodha", balance = "46991.50", date = "2026-09-05")),
        sms(CARD_SMS, "Dear BOBCARD User, Payment of Rs.10,000.00 received towards your BOBCARD ending 9012 on 19-09-2026. Thank you.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        sms(CARD_SMS, "Dear BOBCARD User, Rs.349.00 spent on your BOBCARD ending 9012 at UBER on 20-09-2026. Avl limit Rs.48,152.00",
            debit("349.00", "9012", merchant = "Uber", date = "2026-09-20", kind = CARD)),

        sms(CARD_SMS, "Dear BOBCARD User, 604218 is the OTP for your transaction of Rs.1,499.00 at AMAZON. Do not share it with anyone."),
        sms(SMS, "Your transaction of Rs.500.00 at FLIPKART on A/C XXXXXX1234 has been declined due to insufficient balance - BOB"),
        sms(SMS, "Congratulations! You are eligible for a pre-approved Baroda Personal Loan of up to Rs.5 lakh. Apply now: bankofbaroda.in/pl"),

        email(MAIL, "Dear Customer,\nRs.250.00 has been debited from your A/c XXXXXX1234 on 25-09-2026 for UPI payment to swiggy@icici. Ref No: 526812345678.",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Customer,\nRs.45,000.00 has been credited to your A/c XXXXXX1234 on 01-09-2026 by NEFT from ACME CORP. UTR No: CITIN52026090112345.",
            credit("45000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", date = "2026-09-01")),
        email(CARD_MAIL, "Dear BOBCARD User,\nYour BOBCARD ending 9012 has been used for Rs.1,499.00 at AMAZON on 22-09-2026 18:10:05.",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nRs.2,000.00 has been withdrawn from your A/c XXXXXX1234 at ATM on 12-09-2026.",
            debit("2000.00", "1234", merchant = "ATM", date = "2026-09-12")),
        email(CARD_MAIL, "Dear BOBCARD User,\nPayment of Rs.10,000.00 has been received towards your BOBCARD ending 9012 on 19-09-2026. Thank you.",
            transfer("10000.00", "9012", date = "2026-09-19")),
        email(CARD_MAIL, "Dear BOBCARD User, your statement is generated. Total Amount Due Rs.6,200.00. Payment due date 08-10-2026."),
    )
}
