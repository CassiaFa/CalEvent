package fr.calevent.core.parse

/**
 * Minimal JSON form of [RecognizedText], used for debug dumps taken on a device and for test
 * fixtures. No dependency: the writer and the reader below only cover this format.
 *
 * `{"width":W,"height":H,"lines":[{"text":"…","box":[l,t,r,b],"confidence":0.9,"angle":0.0,
 * "elements":[{"text":"…","box":[l,t,r,b],"confidence":0.9}]}]}`
 */
object RecognizedTextJson {

    fun write(text: RecognizedText): String = buildString {
        append("{\"width\":").append(text.width).append(",\"height\":").append(text.height)
        append(",\"lines\":[")
        text.lines.forEachIndexed { i, line ->
            if (i > 0) append(",\n")
            append("{\"text\":").appendString(line.text).append(",\"box\":").appendBox(line.box)
            line.confidence?.let { append(",\"confidence\":").append(it) }
            line.angle?.let { append(",\"angle\":").append(it) }
            append(",\"elements\":[")
            line.elements.forEachIndexed { j, e ->
                if (j > 0) append(",")
                append("{\"text\":").appendString(e.text).append(",\"box\":").appendBox(e.box)
                e.confidence?.let { append(",\"confidence\":").append(it) }
                append("}")
            }
            append("]}")
        }
        append("]}")
    }

    /** Parses the format written by [write]. Throws an exception on malformed input. */
    fun read(json: String): RecognizedText {
        val root = requireNotNull(JsonReader(json).readValue() as? Map<*, *>) { "root must be an object" }
        val lines = (root["lines"] as? List<*>).orEmpty().map { raw ->
            val l = raw as Map<*, *>
            TextLine(
                text = l["text"] as String,
                box = toBox(l["box"]),
                confidence = (l["confidence"] as? Double),
                angle = (l["angle"] as? Double),
                elements = (l["elements"] as? List<*>).orEmpty().map { re ->
                    val e = re as Map<*, *>
                    TextElement(e["text"] as String, toBox(e["box"]), e["confidence"] as? Double)
                },
            )
        }
        return RecognizedText((root["width"] as Double).toInt(), (root["height"] as Double).toInt(), lines)
    }

    private fun toBox(raw: Any?): Box {
        val v = (raw as List<*>).map { it as Double }
        require(v.size == 4) { "box must have 4 numbers" }
        return Box(v[0], v[1], v[2], v[3])
    }

    private fun StringBuilder.appendBox(b: Box): StringBuilder =
        append('[').append(b.left).append(',').append(b.top).append(',')
            .append(b.right).append(',').append(b.bottom).append(']')

    private fun StringBuilder.appendString(s: String): StringBuilder {
        append('"')
        s.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        return append('"')
    }

    /** Tiny recursive-descent JSON reader: objects, arrays, strings, numbers, booleans, null. */
    private class JsonReader(private val s: String) {
        private var i = 0

        fun readValue(): Any? {
            skipWs()
            require(i < s.length) { "unexpected end of JSON" }
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected '$c' at $i")
            }
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = readString()
                skipWs(); expect(':')
                map[key] = readValue()
                skipWs()
                if (peek() == ',') { i++; continue }
                expect('}'); return map
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipWs()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += readValue()
                skipWs()
                if (peek() == ',') { i++; continue }
                expect(']'); return list
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return value
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        private fun expect(c: Char) { require(peek() == c) { "expected '$c' at $i" }; i++ }
        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun error(msg: String): Nothing = throw IllegalArgumentException(msg)
    }
}
