package com.hisaab.app.ui.more

import kotlin.math.abs

/**
 * A compact QR Code generator: byte mode, error-correction level M, versions 1-20, mask chosen by a
 * simplified penalty score. Enough for a UPI link; no dependency.
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {
    /** True for a dark module. */
    fun dark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26)
        private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16)
        private const val MAX_VERSION = 20

        /** Encodes [text] as UTF-8 bytes. Throws if it doesn't fit version 20 (about 666 bytes). */
        fun encode(text: String): QrCode {
            val bytes = text.toByteArray(Charsets.UTF_8)
            var ver = 1
            while (true) {
                val cap = dataCodewords(ver) * 8
                val countBits = if (ver < 10) 8 else 16
                if (4 + countBits + bytes.size * 8 <= cap) break
                require(++ver <= MAX_VERSION) { "Text too long for QR" }
            }
            return build(ver, interleave(buildData(bytes, ver), ver))
        }

        internal fun formatBits(mask: Int): Int {
            var rem = mask // level M is 0b00
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((mask shl 10) or rem) xor 0x5412
        }

        private fun rawModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        private fun dataCodewords(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * BLOCKS[ver]

        private fun buildData(bytes: ByteArray, ver: Int): ByteArray {
            val bits = ArrayList<Boolean>()
            fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.add(((v ushr i) and 1) == 1) }
            put(0b0100, 4)
            put(bytes.size, if (ver < 10) 8 else 16)
            bytes.forEach { put(it.toInt() and 0xFF, 8) }
            val cap = dataCodewords(ver) * 8
            repeat(minOf(4, cap - bits.size)) { bits.add(false) }
            while (bits.size % 8 != 0) bits.add(false)
            val out = ByteArray(cap / 8)
            for (i in bits.indices) if (bits[i]) out[i ushr 3] = (out[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            var pad = 0xEC
            for (i in bits.size / 8 until out.size) { out[i] = pad.toByte(); pad = pad xor (0xEC xor 0x11) }
            return out
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun divisor(degree: Int): IntArray {
            val r = IntArray(degree)
            r[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    r[j] = gfMul(r[j], root)
                    if (j + 1 < degree) r[j] = r[j] xor r[j + 1]
                }
                root = gfMul(root, 2)
            }
            return r
        }

        /** Reed-Solomon remainder of [data] for [degree] error-correction codewords. */
        internal fun remainder(data: IntArray, degree: Int): IntArray {
            val div = divisor(degree)
            val r = IntArray(degree)
            for (b in data) {
                val f = b xor r[0]
                for (i in 0 until degree - 1) r[i] = r[i + 1]
                r[degree - 1] = 0
                for (i in 0 until degree) r[i] = r[i] xor gfMul(div[i], f)
            }
            return r
        }

        private fun interleave(data: ByteArray, ver: Int): ByteArray {
            val nb = BLOCKS[ver]
            val ecc = ECC_PER_BLOCK[ver]
            val raw = rawModules(ver) / 8
            val numShort = nb - raw % nb
            val shortLen = raw / nb
            val blocks = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until nb) {
                val len = shortLen - ecc + if (i < numShort) 0 else 1
                val dat = IntArray(len) { data[k + it].toInt() and 0xFF }
                k += len
                val e = remainder(dat, ecc)
                blocks.add((if (i < numShort) dat + 0 else dat) + e)
            }
            val out = ArrayList<Byte>()
            for (i in 0 until blocks[0].size) for (j in blocks.indices) {
                if (i != shortLen - ecc || j >= numShort) out.add(blocks[j][i].toByte())
            }
            return out.toByteArray()
        }

        private fun alignPositions(ver: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = if (ver == 32) 26 else (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val r = IntArray(n)
            r[0] = 6
            var pos = ver * 4 + 10
            for (i in n - 1 downTo 1) { r[i] = pos; pos -= step }
            return r
        }

        private fun build(ver: Int, codewords: ByteArray): QrCode {
            val size = ver * 4 + 17
            val m = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) }
            fun set(x: Int, y: Int, v: Boolean) { m[y][x] = v; fn[y][x] = true }
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val d = maxOf(abs(dx), abs(dy))
                        set(x, y, d != 2 && d != 4)
                    }
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val ap = alignPositions(ver)
            for (i in ap.indices) for (j in ap.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == ap.size - 1) || (i == ap.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(ap[i] + dx, ap[j] + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
            fun drawFormat(mask: Int) {
                val b = formatBits(mask)
                fun bit(i: Int) = ((b ushr i) and 1) == 1
                for (i in 0..5) set(8, i, bit(i))
                set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
                for (i in 9..14) set(14 - i, 8, bit(i))
                for (i in 0..7) set(size - 1 - i, 8, bit(i))
                for (i in 8..14) set(8, size - 15 + i, bit(i))
                set(8, size - 8, true)
            }
            drawFormat(0)
            if (ver >= 7) {
                var rem = ver
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (ver shl 12) or rem
                for (i in 0..17) {
                    val bit = ((bits ushr i) and 1) == 1
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val y = if (((right + 1) and 2) == 0) size - 1 - vert else vert
                    if (!fn[y][x] && i < codewords.size * 8) {
                        m[y][x] = ((codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                        i++
                    }
                }
                right -= 2
            }
            var best = 0
            var bestScore = Int.MAX_VALUE
            for (mask in 0..7) {
                applyMask(m, fn, mask)
                val score = penalty(m)
                if (score < bestScore) { bestScore = score; best = mask }
                applyMask(m, fn, mask)
            }
            applyMask(m, fn, best)
            drawFormat(best)
            return QrCode(size, m)
        }

        private fun applyMask(m: Array<BooleanArray>, fn: Array<BooleanArray>, mask: Int) {
            for (y in m.indices) for (x in m.indices) {
                val inv = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv && !fn[y][x]) m[y][x] = !m[y][x]
            }
        }

        /** Simplified penalty: long runs, 2x2 blocks and dark/light imbalance. Any mask is valid; this picks a good one. */
        private fun penalty(m: Array<BooleanArray>): Int {
            val n = m.size
            var p = 0
            var dark = 0
            for (a in 0 until n) {
                var runR = 1
                var runC = 1
                for (b in 1 until n) {
                    runR = if (m[a][b] == m[a][b - 1]) runR + 1 else 1
                    runC = if (m[b][a] == m[b - 1][a]) runC + 1 else 1
                    if (runR == 5) p += 3 else if (runR > 5) p++
                    if (runC == 5) p += 3 else if (runC > 5) p++
                }
                for (b in 0 until n) if (m[a][b]) dark++
            }
            for (y in 0 until n - 1) for (x in 0 until n - 1) {
                if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) p += 3
            }
            p += abs(dark * 20 - n * n * 10) / (n * n) * 10
            return p
        }
    }
}
