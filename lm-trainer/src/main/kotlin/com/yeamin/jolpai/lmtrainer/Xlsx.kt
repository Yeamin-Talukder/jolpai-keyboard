package com.yeamin.jolpai.lmtrainer

import java.io.File
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Reads the first worksheet of an .xlsx file row by row (an .xlsx is a zip of XML files):
 * each row as column letter → cell text. Streams the XML, so large sheets are fine.
 */
object Xlsx {
    fun rows(file: File, sheet: Int = 1): Sequence<Map<String, String>> = sequence {
        ZipFile(file).use { zip ->
            val shared = ArrayList<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { e ->
                val r = XMLInputFactory.newInstance().createXMLStreamReader(zip.getInputStream(e))
                val sb = StringBuilder()
                var inSi = false
                var inT = false
                while (r.hasNext()) {
                    when (r.next()) {
                        XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                            "si" -> { inSi = true; sb.setLength(0) }
                            "t" -> inT = inSi
                            "rPh" -> inSi = false // phonetic hints are not part of the text
                        }
                        XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inT) sb.append(r.text)
                        XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                            "t" -> inT = false
                            "rPh" -> inSi = true
                            "si" -> { shared += sb.toString(); inSi = false }
                        }
                    }
                }
            }
            val entry = zip.getEntry("xl/worksheets/sheet$sheet.xml") ?: return@use
            val r = XMLInputFactory.newInstance().createXMLStreamReader(zip.getInputStream(entry))
            var row = HashMap<String, String>()
            var col = ""
            var type = ""
            val value = StringBuilder()
            var inValue = false
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "row" -> row = HashMap()
                        "c" -> {
                            col = (r.getAttributeValue(null, "r") ?: "").takeWhile { it.isLetter() }
                            type = r.getAttributeValue(null, "t") ?: ""
                            value.setLength(0)
                        }
                        "v", "t" -> inValue = true
                    }
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inValue) value.append(r.text)
                    XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                        "v", "t" -> inValue = false
                        "c" -> {
                            val text = if (type == "s") shared.getOrElse(value.toString().trim().toIntOrNull() ?: -1) { "" } else value.toString()
                            if (text.isNotEmpty()) row[col] = text
                        }
                        "row" -> yield(row)
                    }
                }
            }
        }
    }
}
