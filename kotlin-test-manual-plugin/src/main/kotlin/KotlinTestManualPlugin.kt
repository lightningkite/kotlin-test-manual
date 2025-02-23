package com.lightningkite.testing.manual

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

// Test Note

class KotlinTestManualPlugin : Plugin<Project> {
    val regex = Regex(""" assertManualReview\s*\(\s*(?:file\s*=\s*)?"([a-zA-Z.0-9_\-\/]*)"\s*,\s*(?:currentHash\s*=\s*)?"([a-zA-Z.0-9_-]*)" """.trim())
    val lineComments = Regex(""" \/\/[^\n]+ """.trim())
    val multilineComments = Regex(""" (\/\*\*)(.|\n)+?(\*\/) """.trim())
    override fun apply(project: Project) = with(project) {
        tasks.create("testUpdateHashes") {
            group = "verification"
            val t = this
            tasks.matching { it.name.contains("test", true) && it.name.contains("compile", true) && it.name.contains("kotlin", true) }.configureEach {
                this.dependsOn(t)
            }
            doLast {
                val filePaths = file("src").listFiles()!!
                    .asSequence()
                    .filter { !it.name.contains("test", true) }
                    .flatMap { it.walkTopDown() }
                    .filter { it.extension == "kt" }
                    .sortedByDescending { it.length() }
                file("src").listFiles()!!
                    .asSequence()
                    .filter { it.name.contains("test", true) }
                    .flatMap { it.walkTopDown() }
                    .filter { it.extension == "kt" }
                    .forEach { file ->
                        file.readText().replace(regex) { it ->
                            val fileChecksum = filePaths.filter { f -> f.endsWith(it.groupValues[1]) }.singleOrNull()
                                ?.let {
                                    val rawContent = it.readText().replace(lineComments, "").replace(multilineComments, "").filter { !it.isWhitespace() }
                                    println("Raw: $rawContent")
                                    val digested = MessageDigest.getInstance("SHA-1").digest(rawContent.toByteArray())
                                    HashUtils.encodeHex(digested, toLowerCase = true).let(::String)
                                }
                                ?: throw IllegalStateException("File $file references ${it.groupValues[1]}, which is either vague or non-existent.")
                            it.value.substring(
                                0,
                                it.groups[2]!!.range.start - it.range.start,
                            ) + fileChecksum + it.value.substring(
                                it.groups[2]!!.range.endInclusive - it.range.start + 1
                            )
                        }.let(file::writeText)
                    }
            }

        }
        Unit
    }

}


private object HashUtils {

    const val STREAM_BUFFER_LENGTH = 1024

    fun getCheckSumFromFile(digest: MessageDigest, filePath: String): String {
        val file = File(filePath)
        return getCheckSumFromFile(digest, file)
    }

    fun getCheckSumFromFile(digest: MessageDigest, file: File): String {
        val fis = FileInputStream(file)
        val byteArray = updateDigest(digest, fis).digest()
        fis.close()
        val hexCode = encodeHex(byteArray, true)
        return String(hexCode)
    }

    /**
     * Reads through an InputStream and updates the digest for the data
     *
     * @param digest The MessageDigest to use (e.g. MD5)
     * @param data Data to digest
     * @return the digest
     */
    private fun updateDigest(digest: MessageDigest, data: InputStream): MessageDigest {
        val buffer = ByteArray(STREAM_BUFFER_LENGTH)
        var read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        while (read > -1) {
            digest.update(buffer, 0, read)
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        }
        return digest
    }

    /**
     * Used to build output as Hex
     */
    private val DIGITS_LOWER =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    /**
     * Used to build output as Hex
     */
    private val DIGITS_UPPER =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @param toLowerCase `true` converts to lowercase, `false` to uppercase
     * @return A char[] containing hexadecimal characters in the selected case
     */
    fun encodeHex(data: ByteArray, toLowerCase: Boolean): CharArray {
        return encodeHex(data, if (toLowerCase) DIGITS_LOWER else DIGITS_UPPER)
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @param toDigits the output alphabet (must contain at least 16 chars)
     * @return A char[] containing the appropriate characters from the alphabet
     *         For best results, this should be either upper- or lower-case hex.
     */
    fun encodeHex(data: ByteArray, toDigits: CharArray): CharArray {
        val l = data.size
        val out = CharArray(l shl 1)
        // two characters form the hex value.
        var i = 0
        var j = 0
        while (i < l) {
            out[j++] = toDigits[0xF0 and data[i].toInt() ushr 4]
            out[j++] = toDigits[0x0F and data[i].toInt()]
            i++
        }
        return out
    }
}