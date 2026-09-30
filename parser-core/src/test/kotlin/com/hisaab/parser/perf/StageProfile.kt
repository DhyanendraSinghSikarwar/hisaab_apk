package com.hisaab.parser.perf

import com.hisaab.parser.corpus.AllCorpora
import com.hisaab.parser.extract.*
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.rules.RejectionRules
import com.hisaab.parser.text.TextNormalizer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("perf")
class StageProfile {
    @Test
    fun stages() {
        val texts = AllCorpora.samples.map { TextNormalizer.normalize(it.body) }
        val raw = AllCorpora.samples.map { it.body }
        fun time(name: String, f: (String, Int) -> Unit) {
            repeat(200) { texts.forEachIndexed { i, t -> f(t, i) } }
            val n = 2000
            val s = System.nanoTime()
            repeat(n) { texts.forEachIndexed { i, t -> f(t, i) } }
            println("%-12s %6.1f µs".format(name, (System.nanoTime() - s) / 1000.0 / (n * texts.size)))
        }
        time("normalize") { _, i -> TextNormalizer.normalize(raw[i]) }
        time("reject") { t, _ -> RejectionRules.shouldReject(t) }
        time("amount") { t, _ -> AmountExtractor.extract(t) }
        time("type") { t, _ -> TypeClassifier.classify(t) }
        time("invest") { t, _ -> TypeClassifier.looksLikeInvestment(t) }
        time("account") { t, _ -> AccountExtractor.extract(t) }
        time("ref") { t, _ -> ReferenceExtractor.extract(t) }
        time("channel") { t, _ -> ChannelDetector.detect(t) }
        time("merchant") { t, _ -> MerchantExtractor.extract(t, TransactionType.DEBIT) }
        time("balance") { t, _ -> BalanceExtractor.balance(t); BalanceExtractor.limit(t) }
        time("date") { t, _ -> DateTimeExtractor.extract(t) }
    }
}
