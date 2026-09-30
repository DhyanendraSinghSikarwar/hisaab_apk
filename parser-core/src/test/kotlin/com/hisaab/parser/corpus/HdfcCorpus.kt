package com.hisaab.parser.corpus

object HdfcCorpus {
    private const val SMS = "VM-HDFCBK"
    private const val MAIL = "HDFC Bank InstaAlerts <alerts@hdfcbank.net>"

    val samples = listOf(
        sms(SMS, "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 25/09/26\nRef 526812345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            debit("250.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        sms("AD-HDFCBK-S", "Rs.1500.00 spent on HDFC Bank Card x5678 at AMAZON PAY INDIA on 2026-09-24:18:22:10. Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5678 to 7308080808",
            debit("1500.00", "5678", merchant = "Amazon", date = "2026-09-24", kind = CARD)),
        sms(SMS, "Credit Alert!\nRs.45000.00 credited to HDFC Bank A/c XX1234 on 01-09-26 from VPA employer@okhdfcbank (UPI 524412345678)",
            credit("45000.00", "1234", ref = "524412345678", merchant = "Employer", date = "2026-09-01")),
        sms(SMS, "Update! INR 5,000.00 deposited in HDFC Bank A/c XX1234 on 01-SEP-26 for NEFT Cr-CITI0000001-ACME CORP-XXXXXX-CITIN52026090112345. Avl bal INR 52,340.50. Cheque deposits in A/C are subject to clearing",
            credit("5000.00", "1234", ref = "CITIN52026090112345", merchant = "Acme Corp", balance = "52340.50", date = "2026-09-01")),
        sms(SMS, "Money Received - INR 1,200.00 in your HDFC Bank A/c xx1234 on 20-09-26 by A/c linked to mobile 9XXXXXX123 (IMPS Ref No. 626312345678) Avl bal:INR 10,450.00",
            credit("1200.00", "1234", ref = "626312345678", balance = "10450.00", date = "2026-09-20")),
        sms(SMS, "Rs.2,000.00 withdrawn from HDFC Bank A/c XX1234 at ATM MUMBAI ANDHERI on 12SEP26 14:35. Avl Bal Rs.8,450.00",
            debit("2000.00", "1234", merchant = "ATM", balance = "8450.00", date = "2026-09-12")),
        sms(SMS, "Amt Sent Rs.599.00\nFrom HDFC Bank A/C *1234\nTo NETFLIX\nOn 15/09/26\nRef 525812345000\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            debit("599.00", "1234", ref = "525812345000", merchant = "Netflix", date = "2026-09-15")),
        sms(SMS, "UPDATE: INR 999.00 debited from HDFC Bank XX1234 on 05-SEP-26. Info: ACH D- TP ACH ZERODHA-1234567. Avl bal:INR 23,410.00",
            invest("999.00", "1234", merchant = "Zerodha", balance = "23410.00", date = "2026-09-05")),
        sms(SMS, "Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card XX5678 on 18-09-26.",
            transfer("12345.00", "5678", date = "2026-09-18")),
        sms(SMS, "Rs.349.00 spent via HDFC Bank Card xx5678 at UBER INDIA on 2026-09-20:22:01:45 Avl Lmt: Rs 1,23,456.00",
            debit("349.00", "5678", merchant = "Uber", date = "2026-09-20", kind = CARD)),
        sms(SMS, "Rs.120.00 debited from a/c **1234 on 23-09-26 to VPA zomato-order@ptybl(UPI Ref No 526612349876).",
            debit("120.00", "1234", ref = "526612349876", merchant = "Zomato", date = "2026-09-23")),

        // Negatives
        sms(SMS, "123456 is your OTP for txn of INR 1,500.00 at AMAZON on HDFC Bank card ending 5678. Valid for 5 mins. Do not share OTP with anyone."),
        sms(SMS, "Get a Personal Loan of up to Rs.40 lakh, pre-approved for you! Apply now: hdfcbk.io/x7Yt. T&C apply"),
        sms(SMS, "Transaction of Rs.500.00 on HDFC Bank Card x5678 at FLIPKART has been declined due to insufficient balance."),
        sms(SMS, "HDFC Bank Credit Card XX5678 statement: Total Amt Due Rs.12,345.00, Min Amt Due Rs.620.00, due by 05-10-26. Pay now: hdfcbk.io/pay"),
        sms(SMS, "E-mandate for Rs.1,500.00 for SIP with ZERODHA has been registered successfully on HDFC Bank A/c XX1234."),
        sms(SMS, "Rs.599.00 will be debited from HDFC Bank A/c XX1234 on 01-10-26 towards NETFLIX mandate."),

        email(MAIL, "Dear Customer,\n\nRs.450.00 has been debited from account **1234 to VPA swiggy@icici SWIGGY on 25-09-26.\n\nYour UPI transaction reference number is 526812345678.\n\nIf you did not authorize this transaction, please report it immediately by calling 18002586161.\n\nWarm Regards,\nHDFC Bank",
            debit("450.00", "1234", ref = "526812345678", merchant = "Swiggy", date = "2026-09-25")),
        email(MAIL, "Dear Card Member,\nThank you for using your HDFC Bank Credit Card ending 5678 for Rs 1,500.00 at AMAZON PAY INDIA on 24-09-2026 18:22:10.\nAuthorization code:- 012345\nAfter the above transaction, the available balance on your card is Rs 98,500.00",
            debit("1500.00", "5678", merchant = "Amazon", date = "2026-09-24", kind = CARD)),
        email(MAIL, "Dear Customer,\nRs.45000.00 has been credited to your account **1234 by VPA employer@okhdfcbank ACME CORP on 01-09-26.\nYour UPI transaction reference number is 524412345678.",
            credit("45000.00", "1234", ref = "524412345678", merchant = "Acme Corp", date = "2026-09-01")),
        email(MAIL, "Dear Customer,\nThis is to inform you that an amount of Rs. 999.00 has been debited from your account No. XXXX1234 on account of ACH D- TP ACH ZERODHA-1234567 on 05-09-2026.\nThe available balance in your account is Rs. 23,410.00.",
            invest("999.00", "1234", merchant = "Zerodha", balance = "23410.00", date = "2026-09-05")),
        email(MAIL, "Dear Card Member,\nPayment of Rs. 12,345.00 has been received towards your HDFC Bank Credit Card ending 5678 on 18-09-2026. Thank you.",
            transfer("12345.00", "5678", date = "2026-09-18")),
        email(MAIL, "Dear Customer, your HDFC Bank Credit Card ending 5678 statement for September 2026 is ready. Total Amount Due: Rs. 12,345.00. Minimum Amount Due: Rs. 620.00. Payment Due Date: 05-10-2026."),
        email(MAIL, "Dear Customer, 482913 is the One Time Password (OTP) for your transaction of Rs. 2,499.00 at MYNTRA on HDFC Bank Card ending 5678. Do not share this OTP with anyone."),
    )
}
