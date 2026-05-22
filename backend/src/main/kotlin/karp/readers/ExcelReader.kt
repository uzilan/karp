package karp.readers

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class ExcelReader : BaseReader {

    override val extensions = listOf(".xlsx", ".xls")

    override fun read(path: Path): ReadResult {
        val sb = StringBuilder()
        val sheetNames = mutableListOf<String>()

        WorkbookFactory.create(path.toFile()).use { wb ->
            for (i in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(i)
                sheetNames.add(sheet.sheetName)
                sb.appendLine("## Sheet: ${sheet.sheetName}")

                val rows = sheet.toList()
                val limit = if (rows.size > 1000) {
                    sb.appendLine("[truncated to first 1000 rows of ${rows.size}]")
                    1000
                } else rows.size

                rows.take(limit).forEach { row ->
                    val cells = (0..row.lastCellNum).map { ci ->
                        row.getCell(ci)?.let { cell ->
                            when (cell.cellType) {
                                CellType.NUMERIC -> cell.numericCellValue.toString()
                                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                else -> cell.stringCellValue
                            }
                        } ?: ""
                    }
                    sb.appendLine(cells.joinToString(" | "))
                }
                sb.appendLine()
            }
        }

        val text = sb.toString()
        return ReadResult(
            text = text,
            metadata = mapOf("sheets" to sheetNames, "fileName" to path.fileName.toString()),
            preview = "Excel file with ${sheetNames.size} sheet(s): ${sheetNames.joinToString(", ")}"
        )
    }
}
