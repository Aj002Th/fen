package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.FenBackup
import app.fen.data.FenBackupCodec
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 导入文件的识别结果。 */
sealed interface ParsedFile {
    data class Backup(val backup: FenBackup) : ParsedFile
    data class Sheet(val result: SheetResult) : ParsedFile
}

object FileDecode {

    private const val MAX_BYTES = 64 * 1024 * 1024

    fun parse(fileName: String, bytes: ByteArray): ParsedFile? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null

        if (isZip(bytes)) {
            val table = Xlsx.read(bytes) ?: return null
            return ParsedFile.Sheet(SheetImport.parse(table, source = "iCost Excel"))
        }

        val text = decodeText(bytes)
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.startsWith("{")) {
            FenBackupCodec.decode(trimmed)?.let { return ParsedFile.Backup(it) }
            return null
        }
        val table = Csv.parse(text)
        return ParsedFile.Sheet(SheetImport.parse(table, source = "CSV"))
    }

    fun sessionFrom(fileName: String, parsed: ParsedFile, categories: List<Category>): ImportSession =
        when (parsed) {
            is ParsedFile.Backup -> ImportSession(
                fileName = fileName,
                source = "Fen 备份",
                backup = parsed.backup,
                records = emptyList(),
                skipped = emptyList(),
                warnings = emptyList(),
                mappings = emptyList(),
            )
            is ParsedFile.Sheet -> ImportSession(
                fileName = fileName,
                source = parsed.result.source,
                backup = null,
                records = parsed.result.records,
                skipped = parsed.result.skipped,
                warnings = parsed.result.warnings,
                mappings = ImportPlanner.buildMappings(parsed.result.records, categories),
            )
        }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    /**
     * 中文 CSV 可能是 UTF-8、GBK/GB18030 或 UTF-16（带 BOM）。
     * 先严格按 UTF-8 解码，失败再退回 GB18030。
     */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16LE"))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16BE"))
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030")).removePrefix("\uFEFF")
        }
    }
}
