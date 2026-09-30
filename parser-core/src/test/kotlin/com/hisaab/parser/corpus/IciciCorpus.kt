package com.hisaab.parser.corpus

object IciciCorpus {
    private const val SMS = "AX-ICICIB"
    private const val MAIL = "ICICI Bank <alerts@icicibank.com>"
    private const val CC_MAIL = "ICICI Bank Credit Cards <credit_cards@icicibank.com>"

    val samples = listOf(
        sms(SMS, "ICICI Bank Acct XX123 debited for Rs 240.00 on 23-Sep-26; SWIGGY credited. UPI:526612345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
            debit("240.00", "123", ref = "526612345678", merchant = "Swiggy", date = "2026-09-23")),
        sms(SMS, "Dear Customer, Acct XX123 is credited with Rs 5,000.00 on 20-Sep-26 from JOHN DOE. UPI:526312345678-ICICI Bank.",
            credit("5000.00", "123", ref = "526312345678", merchant = "John Doe", date = "2026-09-20")),
        sms("JM-ICICIT", "INR 1,499.00 spent using ICICI Bank Card XX9012 on 22-Sep-26 on AMAZON. Avl Limit: INR 88,501.00. If not you, call 1800 2662/SMS BLOCK 9012 to 9215676766",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        sms(SMS, "ICICI Bank Acct XX123 debited for Rs 10,000.00 on 21-Sep-26; RAHUL SHARMA credited. IMPS:626412345678. Call 18002662 for dispute.",
            debit("10000.00", "123", ref = "626412345678", merchant = "Rahul Sharma", date = "2026-09-21")),
        sms(SMS, "Dear Customer, your ICICI Bank Account XX123 has been credited with INR 45,000.00 on 01-Sep-26. Info:NEFT-CITIN52026090112345-ACME CORP. The Available Balance is INR 1,02,345.67.",
            credit("45000.00", "123", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "102345.67", date = "2026-09-01")),
        sms(SMS, "Rs 2,000.00 withdrawn from ICICI Bank Acct XX123 at ATM ICICI MUMBAI on 18-Sep-26. Avl Bal: Rs 8,000.00",
            debit("2000.00", "123", merchant = "ATM", balance = "8000.00", date = "2026-09-18")),
        sms(SMS, "Payment of INR 25,000.00 has been received on your ICICI Bank Credit Card Account 4xxx xxxx xxxx 9012 on 19-Sep-26. Thank you.",
            transfer("25000.00", "9012", date = "2026-09-19")),
        sms(SMS, "Your ICICI Bank Acct XX123 has been debited with INR 499.00 on 15-Sep-26 towards NETFLIX for UPI-Mandate. RRN 525912345678.",
            debit("499.00", "123", ref = "525912345678", merchant = "Netflix", date = "2026-09-15")),
        sms("JM-ICICIT", "INR 3,250.00 spent using ICICI Bank Card XX9012 on 10-Sep-26 on IRCTC. Avl Limit: INR 85,251.00.",
            debit("3250.00", "9012", merchant = "IRCTC", date = "2026-09-10", kind = CARD)),
        sms(SMS, "ICICI Bank Account XX123 credited:Rs. 1,200.00 on 12-Sep-26. Info: UPI-525512345678-AMIT KUMAR. Available Balance is Rs. 9,200.00.",
            credit("1200.00", "123", ref = "525512345678", merchant = "Amit Kumar", balance = "9200.00", date = "2026-09-12")),

        sms(SMS, "123456 is OTP for INR 1,499.00 txn on ICICI Bank Card XX9012 at AMAZON. Valid till 10:32. Do not share OTP for security reasons."),
        sms(SMS, "Congratulations! You are eligible for an ICICI Bank Personal Loan of up to Rs 5,00,000. Apply now: icici.co/abc"),
        sms(SMS, "Transaction of INR 750.00 on ICICI Bank Card XX9012 at MYNTRA has been declined. Call 18002662 for help."),
        sms(SMS, "Your AutoPay mandate for Rs 499.00 towards NETFLIX on ICICI Bank Acct XX123 has been successfully registered."),
        sms(SMS, "RAHUL SHARMA has requested Rs 500.00 from you via UPI on ICICI Bank iMobile. Approve or decline in the app."),

        email(CC_MAIL, "Dear Customer,\nYour ICICI Bank Credit Card XX9012 has been used for a transaction of INR 1,499.00 on Sep 22, 2026 at 18:10:05. Info: AMAZON PAY IN.\nThe Available Credit Limit on your card is INR 88,501.00",
            debit("1499.00", "9012", merchant = "Amazon", date = "2026-09-22", kind = CARD)),
        email(MAIL, "Dear Customer,\nWe wish to inform you that your ICICI Bank Account XX123 has been debited with INR 240.00 on 23-Sep-26. Info: UPI/526612345678/SWIGGY.\nThe Available Balance in your Account is INR 12,345.00.",
            debit("240.00", "123", ref = "526612345678", merchant = "Swiggy", balance = "12345.00", date = "2026-09-23")),
        email(MAIL, "Dear Customer,\nYour ICICI Bank Account XX123 has been credited with INR 45,000.00 on 01-Sep-26. Info: NEFT-CITIN52026090112345-ACME CORP.\nThe Available Balance in your Account is INR 1,02,345.67.",
            credit("45000.00", "123", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "102345.67", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nYour ICICI Bank Account XX123 has been debited with INR 5,000.00 on 05-Sep-26. Info: ACH/ICCL MUTUAL FUND/12345678.\nThe Available Balance in your Account is INR 7,345.67.",
            invest("5000.00", "123", merchant = "ICCL", balance = "7345.67", date = "2026-09-05")),
        email(CC_MAIL, "Dear Customer,\nPayment of INR 25,000.00 has been received on your ICICI Bank Credit Card XX9012 on 19-Sep-26. Thank you for your payment.",
            transfer("25000.00", "9012", date = "2026-09-19")),
        email(CC_MAIL, "Dear Customer, Your ICICI Bank Credit Card XX9012 statement for September 2026 is ready. Total Amount Due: INR 12,450.00. Payment Due Date: 05-Oct-26."),
    )
}
