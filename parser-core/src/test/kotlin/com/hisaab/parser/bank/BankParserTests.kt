package com.hisaab.parser.bank

import com.hisaab.parser.corpus.AuCorpus
import com.hisaab.parser.corpus.AxisCorpus
import com.hisaab.parser.corpus.BobCorpus
import com.hisaab.parser.corpus.HdfcCorpus
import com.hisaab.parser.corpus.IciciCorpus
import com.hisaab.parser.corpus.IdfcCorpus
import com.hisaab.parser.corpus.KotakCorpus
import com.hisaab.parser.corpus.PnbCorpus
import com.hisaab.parser.corpus.SbiCorpus
import com.hisaab.parser.corpus.YesCorpus

class HdfcBankParserTest : BankParserContract() { override val parser = HdfcBankParser(); override val samples = HdfcCorpus.samples }
class IciciBankParserTest : BankParserContract() { override val parser = IciciBankParser(); override val samples = IciciCorpus.samples }
class SbiParserTest : BankParserContract() { override val parser = SbiParser(); override val samples = SbiCorpus.samples }
class AxisBankParserTest : BankParserContract() { override val parser = AxisBankParser(); override val samples = AxisCorpus.samples }
class KotakBankParserTest : BankParserContract() { override val parser = KotakBankParser(); override val samples = KotakCorpus.samples }
class IdfcFirstBankParserTest : BankParserContract() { override val parser = IdfcFirstBankParser(); override val samples = IdfcCorpus.samples }
class YesBankParserTest : BankParserContract() { override val parser = YesBankParser(); override val samples = YesCorpus.samples }
class BankOfBarodaParserTest : BankParserContract() { override val parser = BankOfBarodaParser(); override val samples = BobCorpus.samples }
class PnbParserTest : BankParserContract() { override val parser = PnbParser(); override val samples = PnbCorpus.samples }
class AuBankParserTest : BankParserContract() { override val parser = AuBankParser(); override val samples = AuCorpus.samples }
