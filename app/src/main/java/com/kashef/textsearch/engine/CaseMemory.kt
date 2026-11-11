package com.kashef.textsearch.engine

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A summary of one investigation. It stores the search phrase, when it ran and COUNTS only.
 * It never stores file contents, file names or file paths.
 */
data class CaseRecord(
    val id: String,
    val query: String,
    val mode: String,          // SearchMode name
    val scopeIndex: Int,
    val scopeTitle: String,
    val folders: List<String>, // names of the folders that were searched
    val startedAt: Long,
    val durationMs: Long,
    val scanned: Int,
    val matchedFiles: Int,
    val matchCount: Int,
    val problems: Int,
    val unsupportedDocs: Int,
    val otherSkipped: Int,
    val limitReached: Boolean,
    val status: String,        // finished | cancelled | failed
    val indexSkipped: Int = 0, // files ruled out by the local index without being read
    val fold: Boolean = false, // flexible matching of Arabic letter variants was on
)

/** One record per line, fields separated by TAB; tabs, newlines and backslashes inside fields are escaped. */
object CaseCodec {
    private const val FOLDER_SEP = '\u001F'

    fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> append('\\')
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    fun encode(r: CaseRecord): String = listOf(
        r.id, r.query, r.mode, r.scopeIndex.toString(), r.scopeTitle,
        r.folders.joinToString(FOLDER_SEP.toString()),
        r.startedAt.toString(), r.durationMs.toString(), r.scanned.toString(), r.matchedFiles.toString(),
        r.matchCount.toString(), r.problems.toString(), r.unsupportedDocs.toString(), r.otherSkipped.toString(),
        if (r.limitReached) "1" else "0", r.status, r.indexSkipped.toString(),
        if (r.fold) "1" else "0",
    ).joinToString("\t") { esc(it) }

    /** Returns null for a damaged line, so one bad line never loses the whole memory. */
    fun decode(line: String): CaseRecord? {
        val p = line.split('\t').map { unesc(it) }
        if (p.size != 16 && p.size != 17 && p.size != 18) return null // 16/17 = records saved by older versions
        return try {
            CaseRecord(
                id = p[0], query = p[1], mode = p[2], scopeIndex = p[3].toInt(), scopeTitle = p[4],
                folders = if (p[5].isEmpty()) emptyList() else p[5].split(FOLDER_SEP),
                startedAt = p[6].toLong(), durationMs = p[7].toLong(), scanned = p[8].toInt(),
                matchedFiles = p[9].toInt(), matchCount = p[10].toInt(), problems = p[11].toInt(),
                unsupportedDocs = p[12].toInt(), otherSkipped = p[13].toInt(),
                limitReached = p[14] == "1", status = p[15],
                indexSkipped = if (p.size >= 17) p[16].toInt() else 0,
                fold = p.size == 18 && p[17] == "1",
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/** Local, app-private storage for case records (newest first). */
class MemoryStore(private val file: File) {
    fun load(): List<CaseRecord> = try {
        if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull { CaseCodec.decode(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<CaseRecord>) {
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(list.joinToString("\n") { CaseCodec.encode(it) }, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun deleteAll() {
        file.delete()
    }
}

/** Builds the case report. It only states what the app actually knows. */
object CaseReport {
    data class Section(val title: String, val lines: List<String>)

    fun modeLabel(name: String) = when (name) {
        "Phrase" -> "عبارة كاملة"
        "AllWords" -> "كل الكلمات"
        "AnyWord" -> "أي كلمة"
        else -> name
    }

    fun statusLabel(status: String) = when (status) {
        "finished" -> "انتهى"
        "cancelled" -> "أُوقف"
        "failed" -> "تعثّر"
        else -> status
    }

    fun formatTime(ms: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(ms))

    fun sections(r: CaseRecord): List<Section> {
        val case = Section(
            "القضية",
            listOf(
                "عبارة البحث: «${r.query}»",
                "أسلوب البحث: ${modeLabel(r.mode)}" + if (r.fold) " (مطابقة مرنة لأشكال الحروف العربية)" else "",
                "نطاق البحث: ${r.scopeTitle}",
                "المجلدات: " + if (r.folders.isEmpty()) "غير مسجلة" else r.folders.joinToString("، "),
                "التاريخ: ${formatTime(r.startedAt)}",
            ),
        )
        val tally = Section(
            "الحصيلة",
            listOf(
                "الملفات التي فُحصت فعلًا: ${r.scanned}",
                "الملفات المطابقة: ${r.matchedFiles} (إجمالي المطابقات: ${r.matchCount})",
                "الملفات التي تعذرت قراءتها: ${r.problems}",
            ) + (if (r.indexSkipped > 0) listOf("استُبعد ${r.indexSkipped} ملفًا عبر الفهرس المحلي دون قراءته (لم يتغير منذ فهرسته ولا يحتوي ثلاثيات العبارة).") else emptyList()) + listOf(
                "وقت العملية: ${"%.1f".format(Locale.US, r.durationMs / 1000.0)} ثانية",
            ),
        )
        // The app only says the pass is complete when it knows it is.
        val state = when {
            r.status == "finished" && !r.limitReached ->
                "اكتمل المرور على المجلدات المختارة، للأنواع المدعومة فقط."
            r.limitReached ->
                "توقف البحث عند حد ${ScanAccumulator.MAX_TOTAL_MATCHES} مطابقة؛ لم يكتمل المرور وقد توجد نتائج أخرى."
            r.status == "cancelled" -> "أُوقف البحث قبل اكتمال المرور؛ النتائج جزئية."
            r.status == "failed" -> "تعثّر البحث بخطأ؛ النتائج جزئية."
            else -> "حالة البحث غير معروفة."
        }
        val limits = ArrayList<String>()
        if (r.status == "finished" && !r.limitReached && r.matchedFiles == 0) {
            limits.add("لم تُوجد العبارة في أي ملف فُحص، وفق قواعد المطابقة أدناه.")
        }
        if (r.status == "finished" && r.scanned == 0) {
            limits.add("لم يُفحص أي ملف: تأكد من أن المجلدات المختارة تحتوي أنواعًا مدعومة وأن إذن الوصول ما زال ممنوحًا.")
        }
        if (r.unsupportedDocs > 0) {
            limits.add("${r.unsupportedDocs} ملفًا من نوع PDF أو DOC أو XLS أو PPT أو ما شابهها لم يُفحص لأن قارئه غير مدعوم بعد.")
        }
        if (r.problems > 0) {
            limits.add("${r.problems} ملفًا تعذرت قراءته (إذن أو حجم يفوق 30 ميغابايت أو ملف ثنائي أو خطأ قراءة).")
        }
        if (r.otherSkipped > 0) {
            limits.add("${r.otherSkipped} ملفًا بأنواع خارج هذا النطاق لم يُفحص.")
        }
        limits.add(
            if (r.fold) {
                "المطابقة مرنة: تجمع أشكال الحروف العربية (أ إ آ = ا، ى = ي، ة = ه، ؤ = و، ئ = ي) وتتجاهل حالة الأحرف الإنجليزية والتشكيل والتطويل، وتحسب علامات الترقيم."
            } else {
                "المطابقة تتجاهل حالة الأحرف الإنجليزية والتشكيل والتطويل، وتحسب علامات الترقيم وأشكال الحروف كما هي (مثل أ وا)."
            },
        )
        limits.add("المطابقة داخل السطر الواحد: عبارة موزعة على سطرين لا تُكتشف، والكلمات تُطابق جزئيًا داخل غيرها.")
        limits.add("في مستندات DOCX وODT وPPTX تُعامل الفقرة سطرًا، وفي XLSX يُعامل الصف غير الفارغ سطرًا (خلاياه مفصولة بتبويب).")
        if (r.indexSkipped > 0) {
            limits.add("الاستبعاد عبر الفهرس يعتمد على حجم الملف وتاريخ تعديله؛ إن عُدّل ملف دون أن يتغير أيٌّ منهما فقد لا يلتقط الفهرس التغيير.")
        }
        limits.add("لا يشمل البحث نصوص الصور (OCR) ولا البحث التقريبي بعد.")
        limits.add("يُحتفظ بحد أقصى 50 سطرًا مطابقًا لكل ملف و${ScanAccumulator.MAX_TOTAL_MATCHES} مطابقة إجمالًا.")
        return listOf(case, tally, Section("حالة التحقيق: ${statusLabel(r.status)}", listOf(state)), Section("حدود البحث وأسباب محتملة لعدم العثور", limits))
    }

    fun toText(r: CaseRecord): String = buildString {
        append("تقرير القضية — كاشف النصوص\n")
        for (s in sections(r)) {
            append("\n").append(s.title).append("\n")
            s.lines.forEach { append("• ").append(it).append("\n") }
        }
    }
}
