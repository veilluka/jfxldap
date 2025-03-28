package ch.vilki.jfxldap.backend

import java.io.File
import java.util.*

fun decodeBase64Attributes(filePath: String) {
    val file = File(filePath)
    val lines = file.readLines()
    val updatedLines = lines.map { line ->
        if (line.matches(Regex("^.+::\\s+[A-Za-z0-9+/]+={0,2}$"))) {
            val parts = line.split("::", limit = 2)
            val attributeName = parts[0].trim() // Attribute name
            val base64Content = parts[1].trim() // Base64 content
            val decodedContent = String(Base64.getDecoder().decode(base64Content))
            "$attributeName: $decodedContent"
        } else {
            line
        }
    }
    file.writeText(updatedLines.joinToString("\n"))
}