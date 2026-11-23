package app.n_zik.android.core.rescue

import java.io.InputStream
import java.io.OutputStream

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter

internal object RescueSettingsCsv {
    fun write(outStream: OutputStream, entries: List<Triple<String, String, Any>>) {
        csvWriter().open(outStream) {
            writeRow("Type", "Key", "Value")
            entries.forEach { (type, key, value) -> writeRow(type, key, value) }
        }
    }

    fun read(inStream: InputStream): List<Triple<String, String, String>> =
        csvReader().readAllWithHeader(inStream).mapNotNull { row ->
            val key = row["Key"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Triple(row["Type"].orEmpty(), key, row["Value"].orEmpty())
        }
}
