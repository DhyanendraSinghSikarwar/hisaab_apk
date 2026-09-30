package com.hisaab.parser.bank

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.TransactionType.CREDIT
import com.hisaab.parser.model.TransactionType.DEBIT
import com.hisaab.parser.template.Template
import com.hisaab.parser.template.Template.Companion.AMT

// Templates run on normalized text: one line, "INR " for every rupee spelling, line breaks as ". ".

class HdfcBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "HDFC Bank"
    override val smsHeaders = setOf("HDFCBK", "HDFCBN", "HDFCCC")
    override val emailDomains = setOf("hdfcbank.net", "hdfcbank.com", "hdfcbank.bank.in")
    override val smsTemplates = listOf(
        Template("""Sent\s+INR\s*(?<amt>$AMT)\.?\s+From\s+HDFC\s+Bank\s+A/C\s+[x*]*(?<acct>\d{4})\.?\s+To\s+(?<merchant>.+?)\.?\s+On\s+\d{2}/\d{2}/\d{2}\.?\s+Ref\s+(?<ref>\d{9,14})""", DEBIT),
        Template("""NEFT\s+Cr-[A-Z]{4}0[A-Z0-9]{6}-(?<merchant>[^-]+?)-[^-]*-(?<ref>[A-Z0-9]{12,22})"""),
    )
    override val emailTemplates = listOf(
        Template("""Credit\s+Card\s+ending\s+(?<acct>\d{4})\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
        Template("""INR\s*(?<amt>$AMT)\s+has\s+been\s+debited\s+from\s+account\s+[x*]*(?<acct>\d{4})\s+to\s+VPA\s+(?<vpa>\S+)\s+(?<merchant>.+?)\s+on\s""", DEBIT),
    )
}

class IciciBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "ICICI Bank"
    override val smsHeaders = setOf("ICICIB", "ICICIT", "ICICIO")
    override val emailDomains = setOf("icicibank.com", "icici.bank.in", "icicibank.bank.in")
    override val smsTemplates = listOf(
        Template("""Acct\s+XX(?<acct>\d{3,4})\s+debited\s+(?:for|with)\s+INR\s*(?<amt>$AMT)\s+on\s+[\w-]+;\s*(?<merchant>.+?)\s+credited\.\s*(?:UPI|IMPS|RRN)\s*:?\s*(?<ref>\d{9,14})""", DEBIT),
        Template("""INR\s*(?<amt>$AMT)\s+spent\s+(?:using|on)\s+ICICI\s+Bank\s+Card\s+XX(?<acct>\d{4})\s+on\s+[\w-]+\s+on\s+(?<merchant>.+?)\.(?:\s|$)""", DEBIT, AccountKind.CARD),
    )
    override val emailTemplates = listOf(
        Template("""Credit\s+Card\s+XX(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+a\s+transaction\s+of\s+INR\s*(?<amt>$AMT)""", DEBIT, AccountKind.CARD),
    )
}

class SbiParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "SBI"
    override val smsHeaders = setOf("SBIINB", "SBIUPI", "CBSSBI", "ATMSBI", "SBIPSG", "SBICRD", "SBIBNK")
    override val emailDomains = setOf("alerts.sbi.co.in", "sbi.co.in", "sbicard.com", "sbi.bank.in")
    override val smsTemplates = listOf(
        Template("""A/C\s+X(?<acct>\d{4})\s+debited\s+by\s+(?<amt>$AMT)\s+on\s+date\s+\S+\s+trf\s+to\s+(?<merchant>.+?)\s+Refno\s+(?<ref>\d{9,14})""", DEBIT),
        Template("""A/c\s+X(?<acct>\d{4})-credited\s+by\s+INR\s*(?<amt>$AMT)\s+on\s+\S+\s+transfer\s+from\s+(?<merchant>.+?)\s+Ref\s+No\s+(?<ref>\d{9,14})""", CREDIT),
    )
    override val emailTemplates = listOf(
        Template("""SBI\s+Credit\s+Card\s+ending\s+(?<acct>\d{4})\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class AxisBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Axis Bank"
    override val smsHeaders = setOf("AXISBK", "AXISMR", "AXISCC")
    override val emailDomains = setOf("axisbank.com", "axis.bank.in", "axisbank.bank.in")
    override val smsTemplates = listOf(
        Template("""Spent\.?\s+Card\s+no\.\s+XX(?<acct>\d{4})\.\s+INR\s*(?<amt>$AMT)\.\s+[\d-]+\s+[\d:]+\.\s+(?<merchant>[^.]+?)\.\s+Avl""", DEBIT, AccountKind.CARD),
    )
    override val emailTemplates = listOf(
        Template("""INR\s*(?<amt>$AMT)\s+was\s+spent\s+on\s+your\s+Axis\s+Bank\s+Credit\s+Card\s+no\.\s+XX(?<acct>\d{4})\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class KotakBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Kotak Mahindra Bank"
    override val smsHeaders = setOf("KOTAKB", "KOTAKM", "KMBLTD")
    override val emailDomains = setOf("kotak.com", "kotak.bank.in", "kotakbank.com")
    override val smsTemplates = listOf(
        Template("""Sent\s+INR\s*(?<amt>$AMT)\s+from\s+Kotak\s+Bank\s+AC\s+X(?<acct>\d{4})\s+to\s+(?<vpa>\S+?)\s+on\s+[\d-]+\.\s*UPI\s+Ref\s*:?\s*(?<ref>\d{9,14})""", DEBIT),
        Template("""Received\s+INR\s*(?<amt>$AMT)\s+in\s+your\s+Kotak\s+Bank\s+AC\s+X(?<acct>\d{4})\s+from\s+(?<vpa>\S+?)\s+on\s""", CREDIT),
    )
    override val emailTemplates = listOf(
        Template("""Kotak\s+Credit\s+Card\s+xx(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class IdfcFirstBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "IDFC FIRST Bank"
    override val smsHeaders = setOf("IDFCFB", "IDFCBK")
    override val emailDomains = setOf("idfcfirstbank.com", "idfcfirst.bank.in", "idfcbank.com")
    override val smsTemplates = listOf(
        Template("""debited\s+by\s+INR\s*(?<amt>$AMT)\s+on\s+\S+\s+for\s+(?:UPI\s+txn|IMPS|NEFT)\s+to\s+(?<merchant>.+?)\.(?:\s|$)""", DEBIT),
    )
    override val emailTemplates = listOf(
        Template("""INR\s*(?<amt>$AMT)\s+has\s+been\s+debited\s+from\s+your\s+account\s+XX+(?<acct>\d{4})\s+towards\s+UPI\s+payment\s+to\s+(?<merchant>.+?)\s+on\s""", DEBIT),
    )
}

class YesBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Yes Bank"
    override val smsHeaders = setOf("YESBNK", "YESBKK", "YESBK")
    override val emailDomains = setOf("yesbank.in", "yes.bank.in", "yesbank.bank.in")
    override val smsTemplates = listOf(
        Template("""INR\s*(?<amt>$AMT)\s+debited\s+from\s+YES\s+BANK\s+A/c\s+XX(?<acct>\d{4})\s+on\s+\S+\s+for\s+(?:UPI|IMPS|NEFT)\s+to\s+(?<merchant>.+?)\.(?:\s|$)""", DEBIT),
    )
    override val emailTemplates = listOf(
        Template("""YES\s+BANK\s+Credit\s+Card\s+XX(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class BankOfBarodaParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Bank of Baroda"
    override val smsHeaders = setOf("BOBTXN", "BOBSMS", "BOBCRD", "BOBCMS")
    override val emailDomains = setOf("bankofbaroda.com", "bankofbaroda.co.in", "bobcard.co.in", "bankofbaroda.bank.in")
    override val smsTemplates = listOf(
        // BoB writes UPI alerts as "Dr. from A/C ... and Cr. to <vpa>".
        Template("""INR\s*(?<amt>$AMT)\s+Dr\.?\s+from\s+A/C\s+[x*]*(?<acct>\d{3,4})\s+and\s+Cr\.?\s+to\s+(?<vpa>\S+?)\.\s""", DEBIT),
        Template("""INR\s*(?<amt>$AMT)\s+Cr\.?\s+to\s+A/C\s+[x*]*(?<acct>\d{3,4})\s+from\s+(?<vpa>\S+?)\.\s""", CREDIT),
        Template("""INR\s*(?<amt>$AMT)\s+spent\s+on\s+your\s+BOBCARD\s+ending\s+(?<acct>\d{4})\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
    override val emailTemplates = listOf(
        Template("""BOBCARD\s+ending\s+(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class PnbParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Punjab National Bank"
    override val smsHeaders = setOf("PNBSMS", "PNBBNK", "PUNBNK")
    override val emailDomains = setOf("pnb.co.in", "pnb.bank.in", "pnbindia.in")
    override val smsTemplates = listOf(
        Template("""debited\s+INR\s*(?<amt>$AMT)\s+on\s+\S+\s+by\s+(?:UPI|IMPS)\s+Ref\s+(?<ref>\d{9,14})\s+to\s+(?<merchant>.+?)\.(?:\s|$)""", DEBIT),
        Template("""credited\s+INR\s*(?<amt>$AMT)\s+on\s+\S+\s+by\s+(?:UPI|IMPS)\s+Ref\s+(?<ref>\d{9,14})\s+from\s+(?<merchant>.+?)\.(?:\s|$)""", CREDIT),
    )
    override val emailTemplates = listOf(
        Template("""PNB\s+Credit\s+Card\s+XX(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}

class AuBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "AU Small Finance Bank"
    override val smsHeaders = setOf("AUBANK", "AUSFBL", "AUBNKK")
    override val emailDomains = setOf("aubank.in", "au.bank.in")
    override val smsTemplates = listOf(
        Template("""INR\s*(?<amt>$AMT)\s+debited\s+from\s+AU\s+(?:Small\s+Finance\s+)?Bank\s+A/c\s+XX(?<acct>\d{4})\s+on\s+\S+\s+via\s+(?:UPI|IMPS|NEFT)\s+to\s+(?<merchant>.+?)\.(?:\s|$)""", DEBIT),
    )
    override val emailTemplates = listOf(
        Template("""AU\s+Bank\s+Credit\s+Card\s+XX(?<acct>\d{4})\s+has\s+been\s+used\s+for\s+INR\s*(?<amt>$AMT)\s+at\s+(?<merchant>.+?)\s+on\s""", DEBIT, AccountKind.CARD),
    )
}
