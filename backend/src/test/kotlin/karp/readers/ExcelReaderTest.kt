package karp.readers

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ExcelReaderTest {

    private val reader = ExcelReader()

    @Test
    fun `reads single sheet into text table`(@TempDir dir: Path) {
        val file = dir.resolve("test.xlsx")
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Data")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Name")
                createCell(1).setCellValue("Value")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("Alpha")
                createCell(1).setCellValue("42.0")
            }
            wb.write(file.toFile().outputStream())
        }

        val result = reader.read(file)

        assertTrue(result.text.contains("Name"))
        assertTrue(result.text.contains("Alpha"))
        assertTrue(result.text.contains("Data")) // sheet name
        assertEquals(listOf(".xlsx", ".xls"), reader.extensions)
    }

    @Test
    fun `chunks sheet when over 1000 rows`(@TempDir dir: Path) {
        val file = dir.resolve("big.xlsx")
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Big")
            repeat(1100) { i ->
                sheet.createRow(i).createCell(0).setCellValue("row$i")
            }
            wb.write(file.toFile().outputStream())
        }

        val result = reader.read(file)
        assertTrue(result.text.contains("[truncated"))
    }
}
