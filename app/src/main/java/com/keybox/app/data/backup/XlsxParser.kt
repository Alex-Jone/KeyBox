package com.keybox.app.data.backup

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 轻量 .xlsx 解析器（仅读，零第三方依赖）。
 *
 * .xlsx 本质是 ZIP 打包的 XML。这里用 Android 内置的 ZipInputStream + XmlPullParser
 * 解析第一张工作表的数据，仅提取单元格的字符串值。
 *
 * 结构：
 *  - xl/workbook.xml                     → 记录第一个 sheet 的 r:id
 *  - xl/_rels/workbook.xml.rels          → r:id → 工作表文件路径 映射
 *  - xl/sharedStrings.xml                → 共享字符串表（t="s" 的单元格引用它）
 *  - xl/worksheets/sheetN.xml            → 实际数据（<row><c t="..."><v>值</v></c></row>）
 */
object XlsxParser {

    /** 解析第一张工作表，返回 List<List<String?>>（外层行，内层列，缺失为 null）。 */
    fun parse(input: InputStream): List<List<String?>> {
        // 先把整个 zip 读进内存，便于多次随机读取（xlsx 文件通常很小）
        val bytes = input.readBytes()
        return ZipInputStream(bytes.inputStream()).use { zip ->
            val entries = readAllEntries(zip)
            val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
            val sheetPath = resolveFirstSheetPath(entries)
            if (sheetPath == null) return emptyList()
            val sheetXml = entries[sheetPath] ?: return emptyList()
            parseSheet(sheetXml, sharedStrings)
        }
    }

    /** 把所有 zip entry 读进 map（entry 名 → 字节）。 */
    private fun readAllEntries(zip: ZipInputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                map[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return map
    }

    /** 从 workbook.xml + rels 解析出第一张工作表的路径。 */
    private fun resolveFirstSheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries["xl/workbook.xml"]
        // 第一个 sheet 的 r:id
        val firstRid = workbook?.let { findFirstSheetRid(it) }
        if (firstRid != null) {
            val rels = entries["xl/_rels/workbook.xml.rels"]
                ?: entries["_rels/.rels"]
            val target = rels?.let { findTargetForRid(it, firstRid) }
            if (target != null) {
                return if (target.startsWith("/")) target.removePrefix("/")
                    else if (target.startsWith("xl/")) target
                    else "xl/$target"
            }
        }
        // 兜底：直接找 sheet1（绝大多数 .xlsx 第一张表就是 sheet1）
        return entries["xl/worksheets/sheet1.xml"]?.let { "xl/worksheets/sheet1.xml" }
    }

    /** 找第一个 <sheet r:id="..."> 的 r:id。 */
    private fun findFirstSheetRid(workbookXml: ByteArray): String? {
        var result: String? = null
        parseXml(workbookXml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                if (result == null) {
                    // namespace 关闭时属性名保留前缀 "r:id"；开启时为 "id"
                    result = parser.getAttributeValue(null, "id")
                        ?: parser.getAttributeValue(null, "r:id")
                        ?: parser.getAttributeValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"
                        )
                }
            }
        }
        return result
    }

    /** 从 rels 找指定 Id 对应的 Target。 */
    private fun findTargetForRid(relsXml: ByteArray, rid: String): String? {
        var result: String? = null
        parseXml(relsXml) { parser, event ->
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                if (id == rid) {
                    result = parser.getAttributeValue(null, "Target")
                }
            }
        }
        return result
    }

    /** 解析 sharedStrings.xml → List<String>。 */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var inText = false
        var current = StringBuilder()

        parseXml(xml) { parser, event ->
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> current = StringBuilder()
                        "t" -> inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) current.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inText = false
                        "si" -> result.add(current.toString())
                    }
                }
            }
        }
        return result
    }

    /** 解析工作表，返回行列表。 */
    private fun parseSheet(sheetXml: ByteArray, sharedStrings: List<String>): List<List<String?>> {
        val rows = mutableListOf<MutableList<String?>>()
        var currentRow: MutableList<String?>? = null

        // 单元格解析状态
        var cellType: String? = null       // c 标签的 t 属性
        var cellRef: String? = null        // c 标签的 r 属性（如 "B3"）
        var inV = false
        var vText = StringBuilder()

        parseXml(sheetXml) { parser, event ->
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t")
                            cellRef = parser.getAttributeValue(null, "r")
                            vText = StringBuilder()
                        }
                        "v" -> inV = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV) vText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inV = false
                        "c" -> {
                            val raw = vText.toString()
                            val value = when (cellType) {
                                "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: ""
                                "inlineStr", "str" -> raw
                                else -> raw
                            }
                            // 按列引用放置到正确索引（处理空单元格）
                            val colIdx = columnIndex(cellRef) ?: (currentRow?.size ?: 0)
                            while (currentRow!!.size <= colIdx) currentRow!!.add(null)
                            currentRow!![colIdx] = value
                            cellType = null
                            cellRef = null
                        }
                        "row" -> {
                            currentRow?.let { rows.add(it) }
                            currentRow = null
                        }
                    }
                }
            }
        }
        return rows
    }

    /** 从单元格引用 "B3" 解析列索引（0 起）。 */
    private fun columnIndex(ref: String?): Int? {
        if (ref.isNullOrEmpty()) return null
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return null
        var idx = 0
        for (c in letters) {
            idx = idx * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    /** 通用 XML 解析辅助（回调 START_TAG/TEXT/END_TAG）。 */
    private inline fun parseXml(bytes: ByteArray, onEvent: (XmlPullParser, Int) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(bytes.inputStream(), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG || event == XmlPullParser.END_TAG || event == XmlPullParser.TEXT) {
                onEvent(parser, event)
            }
            event = parser.next()
        }
    }
}
