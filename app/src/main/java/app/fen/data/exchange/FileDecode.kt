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

/** 可以展示给用户的解析错误。 */
class ImportParseException(message: String) : Exception(message)

object FileDecode {

    private const val MAX_BYTES = 64 * 1024 * 1024

    /**
     * 按内容识别文件；[source] 只用于确认类型和给出更准确的提示。
     * 默认 [ImportSource.AUTO] 完全按内容判断。
     */
    fun parse(
        fileName: String,
        bytes: ByteArray,
        source: ImportSource = ImportSource.AUTO,
    ): ParsedFile? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null

        if (isZip(bytes)) {
            if (source == ImportSource.FEN_JSON) {
                throw ImportParseException("这是 Excel 文件，不是 Fen 完整备份。请选择表格类来源，或改用 JSON 备份。")
            }
            val table = Xlsx.read(bytes) ?: return null
            return parsedSheet(table, source)
        }

        val text = decodeText(bytes)
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')

        if (trimmed.startsWith("{")) {
            val backup = FenBackupCodec.decode(trimmed)
                ?: throw ImportParseException("这个 JSON 不是 Fen 完整备份（缺少 app/kind/version）。")
            if (source != ImportSource.AUTO && source != ImportSource.FEN_JSON) {
                throw ImportParseException("这是 Fen 完整备份，请把来源改为「Fen → 完整备份」。")
            }
            return ParsedFile.Backup(backup)
        }

        if (source == ImportSource.FEN_JSON) {
            throw ImportParseException("这个文件不是 Fen 完整备份（应为 .json）。")
        }

        val table = Csv.parse(text)
        return parsedSheet(table, source)
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

    private fun parsedSheet(table: Table, source: ImportSource): ParsedFile.Sheet {
        val label = when {
            source == ImportSource.AUTO -> "自动识别"
            source.group.isBlank() -> source.label
            else -> "${source.group} · ${source.label}"
        }
        val result = SheetImport.parse(table, source = label)
        val warnings = result.warnings.toMutableList()
        when (source) {
            ImportSource.ICOST ->
                if (result.records.isNotEmpty() && result.records.all { it.sourceCategory.isBlank() }) {
                    warnings += "没有读到「一级分类/分类」列，可能不是 iCost 账单。"
                }
            ImportSource.WECHAT ->
                if (result.records.isEmpty()) warnings += "没有读到微信账单记录，请确认导出的是「账单明细」。"
            ImportSource.ALIPAY ->
                if (result.records.isEmpty()) warnings += "没有读到支付宝账单记录，请确认导出的是「交易明细」。"
            ImportSource.JD ->
                if (result.records.isEmpty()) warnings += "没有读到京东账单记录，请确认导出的是「账单明细」。"
            ImportSource.MEITUAN ->
                if (result.records.isEmpty()) warnings += "没有读到美团账单记录，请确认导出的是「账单明细」。"
            ImportSource.FEN_CSV ->
                if (result.records.isEmpty()) warnings += "没有读到 Fen 表格记录，表头应为 日期/类型/金额/分类/备注。"
            ImportSource.SHAYU, ImportSource.YIMU, ImportSource.MUMU,
            ImportSource.QIANJI, ImportSource.TUANTUAN ->
                if (result.records.isEmpty()) warnings += "没有读到 ${source.label} 记录，请确认导出的表头包含日期和金额。"
            else -> Unit
        }
        return ParsedFile.Sheet(result.copy(warnings = warnings))
    }
}
