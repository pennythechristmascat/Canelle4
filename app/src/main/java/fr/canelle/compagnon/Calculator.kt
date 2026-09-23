package fr.canelle.compagnon

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Calculatrice exacte : Canelle l'utilise pour ne jamais se tromper dans un calcul. */
object Calculator {

    fun eval(input: String): Double {
        val parser = Parser(normalize(input))
        val v = parser.expression()
        parser.skipSpaces()
        if (parser.pos < parser.s.length) throw IllegalArgumentException("caractère inattendu « ${parser.s[parser.pos]} »")
        if (v.isNaN() || v.isInfinite()) throw IllegalArgumentException("résultat indéfini")
        return v
    }

    fun format(v: Double): String {
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        val txt = if (abs(v) >= 1e15 || (v != 0.0 && abs(v) < 1e-9)) bd.toString() else bd.toPlainString()
        return txt.replace('.', ',')
    }

    private fun normalize(s: String): String = s.lowercase(Locale.ROOT)
        .replace("×", "*").replace("÷", "/").replace("−", "-")
        .replace("√", "sqrt").replace("π", "pi")
        .replace(Regex("(?<=\\d)[\\s\\u00A0\\u202F]+(?=\\d{3}\\b)"), "") // « 1 000 » -> 1000
        .replace(",", ".")

    private class Parser(val s: String) {
        var pos = 0

        fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (pos < s.length && s[pos] == c) {
                pos++
                return true
            }
            return false
        }

        fun expression(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                v = when {
                    eat('*') -> v * power()
                    eat('/') -> {
                        val d = power()
                        if (d == 0.0) throw IllegalArgumentException("division par zéro")
                        v / d
                    }
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> postfix()
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                v = when {
                    eat('%') -> v / 100.0
                    eat('!') -> factorial(v)
                    else -> return v
                }
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (eat('(')) {
                val v = expression()
                if (!eat(')')) throw IllegalArgumentException("parenthèse manquante")
                return v
            }
            val start = pos
            if (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                if (pos + 1 < s.length && s[pos] == 'e' && (s[pos + 1].isDigit() || s[pos + 1] == '-' || s[pos + 1] == '+')) {
                    pos += 2
                    while (pos < s.length && s[pos].isDigit()) pos++
                }
                return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("nombre invalide")
            }
            while (pos < s.length && s[pos].isLetter()) pos++
            val name = s.substring(start, pos)
            if (name.isEmpty()) {
                throw IllegalArgumentException(if (pos < s.length) "caractère inattendu « ${s[pos]} »" else "expression incomplète")
            }
            if (name == "pi") return PI
            if (name == "e") return E
            if (!eat('(')) throw IllegalArgumentException("parenthèse attendue après $name")
            val a = expression()
            if (!eat(')')) throw IllegalArgumentException("parenthèse manquante")
            return when (name) {
                "sqrt", "racine" -> sqrt(a)
                "sin" -> sin(Math.toRadians(a))
                "cos" -> cos(Math.toRadians(a))
                "tan" -> tan(Math.toRadians(a))
                "ln" -> ln(a)
                "log" -> log10(a)
                "abs" -> abs(a)
                "exp" -> exp(a)
                "round", "arrondi" -> Math.round(a).toDouble()
                else -> throw IllegalArgumentException("fonction inconnue : $name")
            }
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v) || v > 170) throw IllegalArgumentException("factorielle impossible")
            var r = 1.0
            for (i in 2..v.toInt()) r *= i
            return r
        }
    }
}
