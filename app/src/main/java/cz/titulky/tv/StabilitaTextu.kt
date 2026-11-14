package cz.titulky.tv

/** Zkrácení, zmizení a rozepsané pokračování potvrzuje druhé shodné čtení. */
internal class StabilitaTextu {
    private var posledni = emptyList<String>()
    private var podezrely: List<String>? = null

    @Synchronized fun prijmi(text: String): Boolean {
        val slova = SLOVA.findAll(text.lowercase()).map { it.value }.toList()
        if (slova == posledni) { podezrely = null; return true }
        var i = 0
        for (slovo in posledni) if (i < slova.size && slova[i] == slovo) i++
        val zkraceny = slova.size < posledni.size && i == slova.size
        val dopisujeSe = posledni.isNotEmpty() && doplneno(text, posledni.joinToString(" ")) &&
            text.trimEnd().lastOrNull() !in setOf('.', '!', '?', '…', '"', '\'')
        if ((zkraceny || dopisujeSe) && podezrely != slova) { podezrely = slova; return false }
        posledni = slova
        podezrely = null
        return true
    }

    @Synchronized fun reset() { posledni = emptyList(); podezrely = null }

    companion object {
        private val SLOVA = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

        fun doplneno(novy: String, puvodni: String): Boolean {
            val pred = puvodni.lowercase().filter(Char::isLetterOrDigit)
            val ted = novy.lowercase().filter(Char::isLetterOrDigit)
            return pred.isNotEmpty() && ted.length > pred.length && ted.startsWith(pred)
        }

        /** Oprava nebo dočtení stejného titulku nesmí vytvořit další kopii věty. */
        fun opakovaneCteni(predchozi: String, nove: String): Boolean {
            fun slova(text: String) = SLOVA.findAll(text.lowercase())
                .map { it.value.filter(Char::isLetterOrDigit) }.toList()
            val pred = slova(predchozi)
            val ted = slova(nove)
            if (pred.isEmpty() || ted.isEmpty()) return false
            val a = pred.joinToString(" ")
            val b = ted.joinToString(" ")
            if (a.startsWith(b) || b.startsWith(a)) return true
            // shortcut: shodná trojice slov rozpozná i opravu okrajů OCR; význam vět neposuzuje.
            val trojice = pred.windowed(3).toSet()
            return ted.windowed(3).any { it in trojice }
        }
    }
}

/** Platnost viditelného výsledku, nikoli rušení nebo přeskakování síťové fronty. */
internal class PlatnostPrekladu {
    private var verze = 0L
    @Synchronized fun novy(): Long { verze++; return verze }
    @Synchronized fun plati(token: Long) = token == verze
    @Synchronized fun zrus() { verze++ }
}

/** Stávající režim žluté oblasti: celý text, bez patnáctisekundového filtru dialogů. */
internal class TextVRamecku {
    private val stabilita = StabilitaTextu()
    @Volatile var text = ""
        private set
    private var prazdnyOd: Long? = null

    /** null = beze změny, prázdný řetězec = skrýt, jinak přeložit celý nový blok. */
    @Synchronized fun zpracuj(radky: List<String>, zapnuto: Boolean, ted: Long): String? {
        if (!zapnuto) return reset()
        val novy = spoj(radky)
        if (novy.count(Char::isLetter) < 3) {
            if (text.isEmpty()) return null
            val od = prazdnyOd ?: ted.also { prazdnyOd = it }
            return if (ted - od > 1500) reset() else null
        }
        prazdnyOd = null
        if (!stabilita.prijmi(novy) || novy == text) return null
        text = novy
        return novy
    }

    @Synchronized fun reset(): String? {
        val bylText = text.isNotEmpty()
        text = ""
        prazdnyOd = null
        stabilita.reset()
        return if (bylText) "" else null
    }

    companion object {
        fun spoj(radky: List<String>) = radky.map(String::trim).filter(String::isNotEmpty)
            .fold("") { acc, r -> when {
                acc.isEmpty() -> r
                acc.endsWith("-") -> acc.dropLast(1) + r
                else -> "$acc $r"
            } }.replace(Regex("\\s+"), " ").trim()
    }
}
