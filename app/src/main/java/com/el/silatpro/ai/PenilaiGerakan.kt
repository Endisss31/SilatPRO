package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.HasilEvaluasi
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * Pipeline evaluasi gerakan:
 *   1. Global MLP (34 fitur, StandardScaler global) → deteksi jenis + sisi
 *   2. Evaluator MLP spesifik (39 fitur, scaler per model) → Benar/Salah
 *   3. Rule-based feedback tipis dari PengevaluasiRuleBase
 *
 * Input evaluator = 34 normalized keypoints + 5 rule features (side-aware).
 * Scaler per model: 38 elemen (f0–f37 di-scale, f38 identity).
 */
class PenilaiGerakan(private val konteks: Context) {

    companion object {
        private const val TAG = "PenilaiGerakan"
        private const val N_GLOBAL   = 34
        private const val N_EVAL     = 39
        private const val N_GLOBAL_KELAS = 10
        private const val THRESH_GLOBAL = 0.60f
        private const val THRESH_EVAL   = 0.70f

        private val GLOBAL_LABELS = listOf(
            "Pukulan2Kanan","Pukulan2Kiri",
            "Pukulan4Kanan","Pukulan4Kiri",
            "Tangkisan1Kanan","Tangkisan1Kiri",
            "Tangkisan3Kanan","Tangkisan3Kiri",
            "Tendangan2Kanan","Tendangan2Kiri"
        )

        // ── Global scaler (34 fitur) ───────────────────────────────────────
        private val GLOBAL_MEAN = floatArrayOf(
            -0.03124308f,-1.20373208f, 0.05666338f,-1.31635167f,
            -0.14504454f,-1.31262917f, 0.21926405f,-1.25274766f,
            -0.32682752f,-1.23831859f, 0.41792515f,-0.67626064f,
            -0.50093278f,-0.63830166f, 0.85208593f,-0.28356158f,
            -0.76537178f,-0.19856007f, 0.84190639f,-0.24062342f,
            -0.74734138f,-0.19924445f, 0.45043870f, 0.65175862f,
            -0.36731219f, 0.66284585f, 1.32369148f, 1.10081402f,
            -1.17932781f, 1.12756614f, 1.79062666f, 2.14989084f,
            -1.55695225f, 2.26514246f
        )
        private val GLOBAL_SCALE = floatArrayOf(
            0.45086230f,0.25760755f, 0.48683637f,0.26588502f,
            0.48438382f,0.26156461f, 0.48739988f,0.25662335f,
            0.48280296f,0.25153702f, 0.32690599f,0.26230234f,
            0.31956444f,0.24147858f, 0.20536008f,0.59496765f,
            0.22335525f,0.66082728f, 0.54581090f,0.82344910f,
            0.54625885f,0.84026623f, 0.32782304f,0.22839098f,
            0.32216363f,0.24796392f, 0.67490568f,0.48181399f,
            0.56497743f,0.54271814f, 0.86245270f,0.86678741f,
            0.89617494f,0.94543721f
        )

        // ── Pukulan2 scaler (38 elemen, f38 pakai identity) ───────────────
        private val PUKULAN2_MEAN = floatArrayOf(
            -0.05564800f,-1.19125421f, 0.00786648f,-1.29240626f,
            -0.12510535f,-1.29199095f, 0.16002903f,-1.24868980f,
            -0.27370215f,-1.24687202f, 0.45350701f,-0.71492088f,
            -0.54186160f,-0.67351148f, 0.99286545f,-0.46304668f,
            -1.00690600f,-0.37299277f, 1.38694032f,-0.30748388f,
            -1.30886569f,-0.20816453f, 0.46812125f, 0.68534845f,
            -0.38031586f, 0.70252941f, 1.31308480f, 1.14973770f,
            -1.18625413f, 1.18162341f, 1.73004358f, 2.24362317f,
            -1.46397244f, 2.33635443f, 0.93835419f, 0.77579678f,
             0.74492569f, 0.15412762f
        )
        private val PUKULAN2_SCALE = floatArrayOf(
            0.33658847f,0.04904718f, 0.30362511f,0.04670518f,
            0.29928098f,0.05430479f, 0.12806927f,0.04424478f,
            0.12523295f,0.05804729f, 0.01690345f,0.05448631f,
            0.01790854f,0.04130632f, 0.26185100f,0.34411708f,
            0.27693253f,0.29792845f, 0.70845875f,0.65884731f,
            0.73823743f,0.61406701f, 0.02418654f,0.01886387f,
            0.01581731f,0.02693356f, 0.05762616f,0.05066297f,
            0.04607797f,0.08122355f, 0.05432823f,0.06404477f,
            0.05631302f,0.07276088f, 0.02316883f,0.01652588f,
            0.03231525f,0.05570387f
        )

        // ── Pukulan4 scaler ───────────────────────────────────────────────
        private val PUKULAN4_MEAN = floatArrayOf(
            -0.03762346f,-1.30216281f, 0.06436674f,-1.43009164f,
            -0.16251508f,-1.42377067f, 0.24101859f,-1.37687027f,
            -0.36347606f,-1.35868374f, 0.45863440f,-0.71185970f,
            -0.53792775f,-0.67334357f, 0.75032701f, 0.03440323f,
            -0.68611901f, 0.11062972f, 0.46576243f, 0.13225186f,
            -0.48348099f, 0.28192900f, 0.50378131f, 0.69351031f,
            -0.42399269f, 0.69149848f, 1.44938363f, 1.14371062f,
            -1.34361436f, 1.17187905f, 1.91578615f, 2.34985518f,
            -1.78058865f, 2.42040000f, 0.36387774f, 0.75819454f,
             0.75979727f, 0.75375821f
        )
        private val PUKULAN4_SCALE = floatArrayOf(
            0.06340757f,0.02639345f, 0.05948683f,0.02855312f,
            0.05782205f,0.02766459f, 0.04304764f,0.03125231f,
            0.03889244f,0.02812139f, 0.00987475f,0.04637581f,
            0.01290031f,0.03387447f, 0.09012473f,0.08538682f,
            0.07212683f,0.15094618f, 0.09342325f,0.13430177f,
            0.13512136f,0.16562693f, 0.01240611f,0.01678462f,
            0.01185903f,0.02709521f, 0.02656074f,0.03116574f,
            0.02203210f,0.04150781f, 0.08458317f,0.03949700f,
            0.08244380f,0.06920254f, 0.20226942f,0.02626469f,
            0.01909665f,0.13024173f
        )

        // ── Tangkisan1 scaler ─────────────────────────────────────────────
        private val TANGKISAN1_MEAN = floatArrayOf(
            -0.04147041f,-1.39729450f, 0.07134594f,-1.51680779f,
            -0.17082595f,-1.51099066f, 0.25238917f,-1.42234558f,
            -0.36169483f,-1.39584143f, 0.44957348f,-0.78447969f,
            -0.54419265f,-0.70912051f, 0.99710537f,-0.35881358f,
            -0.90440203f,-0.19446180f, 0.83915959f,-0.47765793f,
            -0.71912675f,-0.33859893f, 0.48689621f, 0.73572091f,
            -0.39312500f, 0.75785429f, 1.51838589f, 1.21367441f,
            -1.41441766f, 1.23429147f, 1.88572321f, 2.39183745f,
            -1.70307403f, 2.47619648f, 0.29470761f, 0.73424045f,
             0.71164846f, 0.54879203f
        )
        private val TANGKISAN1_SCALE = floatArrayOf(
            0.01462289f,0.01435906f, 0.01459104f,0.01567937f,
            0.01629341f,0.01585412f, 0.01170140f,0.01660277f,
            0.01073373f,0.01631214f, 0.01822440f,0.04320866f,
            0.01283260f,0.03644945f, 0.22704704f,0.32254446f,
            0.18064406f,0.25347436f, 0.10501264f,0.96514475f,
            0.16762228f,0.88893483f, 0.01425681f,0.00968143f,
            0.01607730f,0.01244796f, 0.03498611f,0.01682625f,
            0.01634790f,0.02733447f, 0.05420019f,0.02845466f,
            0.02158765f,0.04316184f, 0.03661839f,0.00986828f,
            0.01041393f,0.07151392f
        )

        // ── Tangkisan3 scaler ─────────────────────────────────────────────
        private val TANGKISAN3_MEAN = floatArrayOf(
            0.00534439f,-1.39691835f, 0.11163904f,-1.50438008f,
           -0.11240823f,-1.50582602f, 0.27468710f,-1.41270526f,
           -0.30512934f,-1.40860023f, 0.42805662f,-0.82974073f,
           -0.49992773f,-0.80559416f, 0.68750244f,-0.85786250f,
           -0.63367202f,-0.84814050f, 0.37252625f,-0.69044453f,
           -0.25917078f,-0.79547714f, 0.48452214f, 0.81458967f,
           -0.41260701f, 0.82123941f, 1.49328056f, 1.29527005f,
           -1.38187412f, 1.28909936f, 1.97830263f, 2.52980221f,
           -1.75529998f, 2.54481666f, 0.51376092f, 0.76014922f,
            0.73432684f, 0.98001501f
        )
        private val TANGKISAN3_SCALE = floatArrayOf(
            0.08083309f,0.06299597f, 0.07388544f,0.07364985f,
            0.06190051f,0.05648451f, 0.05023004f,0.07470146f,
            0.02811911f,0.05865167f, 0.03099059f,0.16089108f,
            0.02223292f,0.20890094f, 0.03628858f,0.77564845f,
            0.04325669f,0.87440221f, 0.36517087f,1.20389865f,
            0.31036856f,1.27660876f, 0.02602032f,0.05668088f,
            0.02785133f,0.01582718f, 0.02431599f,0.02919330f,
            0.09090152f,0.08268014f, 0.08174027f,0.12819358f,
            0.04885014f,0.09185618f, 0.04983378f,0.01318187f,
            0.00870995f,0.08332471f
        )

        // ── Tendangan2 scaler ─────────────────────────────────────────────
        private val TENDANGAN2_MEAN = floatArrayOf(
            -0.02450674f,-0.73214915f, 0.02871558f,-0.83827672f,
            -0.15138882f,-0.83266197f, 0.16975299f,-0.80317123f,
            -0.32967806f,-0.78394067f, 0.29939565f,-0.33964815f,
            -0.38100167f,-0.33033339f, 0.83307293f, 0.22480604f,
            -0.59473658f, 0.31454153f, 1.14309790f, 0.14114400f,
            -0.96331190f, 0.05955780f, 0.30768097f, 0.32810571f,
            -0.22612119f, 0.34135715f, 0.84471785f, 0.69852761f,
            -0.56787263f, 0.76205588f, 1.43560811f, 1.23327356f,
            -1.07972724f, 1.55109016f, 0.88948259f, 0.91179176f,
             0.90107751f, 0.35130504f
        )
        private val TENDANGAN2_SCALE = floatArrayOf(
            0.94301993f,0.12267859f, 1.03634542f,0.16151531f,
            1.03563532f,0.10826309f, 1.07366188f,0.21962846f,
            1.06745019f,0.17465264f, 0.71515241f,0.40157429f,
            0.70124663f,0.33335365f, 0.05416192f,0.46050381f,
            0.05511822f,0.65377872f, 0.29489584f,0.08340884f,
            0.36628124f,0.18207947f, 0.71415003f,0.33778267f,
            0.70134702f,0.40983757f, 1.39845334f,0.97274656f,
            1.04773152f,1.13172097f, 1.86377593f,1.62034931f,
            1.91183337f,1.94466344f, 0.04459261f,0.03870872f,
            0.04442522f,0.11224816f
        )
    }

    // ── Interpreter ────────────────────────────────────────────────────────────
    private var interpreterGlobal: Interpreter?   = null
    private var interpreterSpesifik: Interpreter? = null

    // ── State ──────────────────────────────────────────────────────────────────
    private var folderGerakan   = ""   // e.g. "Pukulan2"
    private var tipeGerakan     = ""   // "pukulan" | "tangkisan" | "tendangan"
    private var prefixGlobal    = ""   // untuk prefix-matching global label

    private val pengekstrak    = PengekstrakFitur()
    private val ruleBase       = PengevaluasiRuleBase(konteks)
    private var ruleBaseAktif  = false
    private var jumlahFiturEval = N_EVAL  // default 39, bisa 34 jika model lama

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Muat model global + model spesifik.
     * @param pathSpesifik  "Model/Pukulan2/model_pukulan2_mlp.tflite"
     * @param namaJsonRule  "pukulan_2_kanan.json"  (opsional, untuk feedback rule)
     */
    fun muatModel(pathSpesifik: String, namaJsonRule: String = ""): Boolean {
        return try {
            val opsi = Interpreter.Options().apply { numThreads = 2 }
            interpreterGlobal   = Interpreter(muat("GlobalMovement/model_global_mlp.tflite"), opsi)
            interpreterSpesifik = Interpreter(muat(pathSpesifik), opsi)

            // Deteksi jumlah fitur input dari model TFLite
            val inputTensor = interpreterSpesifik!!.getInputTensor(0)
            val inputShape  = inputTensor.shape()   // e.g. [1, 39] atau [1, 34]
            jumlahFiturEval = if (inputShape.size >= 2) inputShape[1] else N_EVAL
            Log.d(TAG, "Model spesifik input shape: ${inputShape.toList()} → $jumlahFiturEval fitur")

            // Parse folder dari path
            val parts = pathSpesifik.split("/")
            if (parts.size >= 2) {
                folderGerakan = parts[1]
                tipeGerakan   = tipeFromFolder(folderGerakan)
                prefixGlobal  = folderGerakan
            }

            ruleBaseAktif = namaJsonRule.isNotEmpty() && ruleBase.muatModel(namaJsonRule)
            Log.d(TAG, "Model dimuat: global + $folderGerakan | fiturEval=$jumlahFiturEval | ruleBase=$ruleBaseAktif")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal muat model '$pathSpesifik': ${e.message}")
            false
        }
    }

    fun muatModel(path: String): Boolean = muatModel(path, "")

    /**
     * Evaluasi pose.
     * @param fitur34   FloatArray(34) dari PengekstrakFitur.ekstrak()
     * @param durasiMs  durasi sesi
     * @param pathAset  untuk log
     * @param pose      DataPose — wajib untuk 39-fitur pipeline
     */
    fun evaluasi(
        fitur34: FloatArray,
        durasiMs: Long,
        pathAset: String,
        pose: DataPose? = null
    ): HasilEvaluasi {
        if (interpreterGlobal == null || interpreterSpesifik == null) {
            return gagal("Model tidak dimuat", durasiMs)
        }

        // ── Step 1: Global model (34 fitur) ──────────────────────────────
        val f34scaled = applyScaler(fitur34, GLOBAL_MEAN, GLOBAL_SCALE)
        val bufGlobal = toBuffer(f34scaled)
        val outGlobal = Array(1) { FloatArray(N_GLOBAL_KELAS) }
        interpreterGlobal!!.run(bufGlobal, outGlobal)

        val prob34 = outGlobal[0]
        var probMax = -1f; var idxMax = -1
        for (i in prob34.indices) { if (prob34[i] > probMax) { probMax = prob34[i]; idxMax = i } }

        val labelGlobal = GLOBAL_LABELS.getOrNull(idxMax) ?: "Tidak Dikenali"
        Log.d(TAG, "Global: $labelGlobal (${(probMax*100).toInt()}%)")

        val gerakanCocok = labelGlobal.startsWith(prefixGlobal) && probMax >= THRESH_GLOBAL

        // ── Rule-base: Gerakan tidak cocok dengan target ─────────────────────
        if (!gerakanCocok) {
            return if (probMax < THRESH_GLOBAL) {
                // Pose terdeteksi tapi confidence terlalu rendah → body tidak terlihat jelas
                gagalDenganKonteks(
                    pesan = "⚠ Gerakan belum terdeteksi\nPastikan seluruh tubuh terlihat di kamera",
                    durasiMs = durasiMs,
                    salah = false,
                    terdeteksi = ""
                )
            } else {
                // Confidence tinggi tapi label BERBEDA dari target → user salah gerakan
                val namaTarget = namaRamahGerakan(folderGerakan)
                val namaSalah  = namaRamahGerakan(labelGlobal)
                gagalDenganKonteks(
                    pesan = buildString {
                        appendLine("✗ Gerakan salah terdeteksi: $namaSalah")
                        appendLine("• Pastikan Anda melakukan: $namaTarget")
                        append(saranPerbaikanGerakan(folderGerakan))
                    }.trim(),
                    durasiMs = durasiMs,
                    salah = true,
                    terdeteksi = labelGlobal
                )
            }
        }

        // ── Step 2: Tentukan sisi dari label global ───────────────────────
        val sisi = if (labelGlobal.endsWith("Kiri")) "kiri" else "kanan"

        // ── Step 3: Bangun fitur sesuai jumlah yang dibutuhkan model ────────
        if (pose == null) return gagal("Pose tidak tersedia untuk evaluasi", durasiMs)

        val fiturUntukEval: FloatArray
        val fiturScaled: FloatArray

        if (jumlahFiturEval >= N_EVAL) {
            // Model baru: 39 fitur (34 normalized + 5 rule features)
            val fiturRule = pengekstrak.hitungFiturRule(fitur34, tipeGerakan, sisi)
            val fitur39   = FloatArray(N_EVAL).also {
                System.arraycopy(fitur34, 0, it, 0, 34)
                System.arraycopy(fiturRule, 0, it, 34, 5)
            }
            val (evalMean, evalScale) = scalerForGerakan(folderGerakan)
            fiturUntukEval = fitur39
            fiturScaled = FloatArray(N_EVAL)
            val n = min(N_EVAL, evalMean.size)
            for (i in 0 until n) {
                fiturScaled[i] = (fitur39[i] - evalMean[i]) /
                    evalScale[i].let { if (it < 1e-9f) 1f else it }
            }
            if (n < N_EVAL) fiturScaled[n] = fitur39[n]
        } else {
            // Model lama: 34 fitur (fallback, gunakan scaler global)
            Log.w(TAG, "Model evaluator menerima $jumlahFiturEval fitur (bukan 39) — pakai 34 fitur")
            fiturUntukEval = fitur34
            fiturScaled = applyScaler(fitur34, GLOBAL_MEAN, GLOBAL_SCALE)
        }

        // ── Step 5: Evaluator spesifik ────────────────────────────────────
        val bufEval = toBuffer(fiturScaled)
        val outEval = Array(1) { FloatArray(2) }
        interpreterSpesifik!!.run(bufEval, outEval)

        val probBenar = outEval[0][0]
        val probSalah = outEval[0][1]
        val skorML    = probBenar * 100.0

        Log.d(TAG, "Eval $labelGlobal: benar=${(probBenar*100).toInt()}%, salah=${(probSalah*100).toInt()}%")

        // ── Step 6: Rule-based feedback (opsional layer) ──────────────────
        val hasilRule = if (ruleBaseAktif) ruleBase.evaluasi(pose) else null

        val skorTotal: Double
        val kategori: String
        val feedbackList = mutableListOf<String>()
        val skorPerFitur = mutableMapOf<String, Double>()

        skorPerFitur["Confidence_Global"] = probMax.toDouble()
        skorPerFitur["ML_Benar"]          = probBenar.toDouble()

        if (hasilRule != null) {
            // Gabung: 60% rule-based + 40% ML
            skorTotal = (hasilRule.skorTotal * 0.6) + (skorML * 0.4)
            kategori  = tentukanKategori(skorTotal)
            skorPerFitur.putAll(hasilRule.skorPerFitur)
            feedbackList.add("✓ $labelGlobal (${(probMax*100).toInt()}%) — Skor: ${skorTotal.toInt()} [$kategori]")
            if (hasilRule.feedbackPerFitur.isEmpty()) {
                feedbackList.add("✅ Semua posisi sudah sesuai acuan!")
            } else {
                feedbackList.addAll(hasilRule.feedbackPerFitur)
            }
        } else {
            // Hanya ML
            skorTotal = skorML
            kategori  = tentukanKategori(skorTotal)
            if (probBenar >= THRESH_EVAL) {
                feedbackList.add("✓ $labelGlobal (${(probMax*100).toInt()}%)\nSkor: ${skorTotal.toInt()} — $kategori")
            } else {
                feedbackList.add("✗ $labelGlobal — Perbaiki postur dan kuda-kuda\nSkor: ${skorTotal.toInt()}")
            }
        }

        return HasilEvaluasi(
            idGerakan    = folderGerakan.lowercase(),
            labelGerakan = folderGerakan,
            skorTotal    = skorTotal,
            kategori     = kategori,
            skorPerFitur = skorPerFitur,
            feedbackList = feedbackList,
            durasiMs     = durasiMs
        )
    }

    fun tutup() {
        interpreterGlobal?.close();   interpreterGlobal   = null
        interpreterSpesifik?.close(); interpreterSpesifik = null
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private fun muat(path: String): MappedByteBuffer {
        val fd = konteks.assets.openFd(path)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun toBuffer(arr: FloatArray): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
        arr.forEach { buf.putFloat(it) }
        buf.rewind()
        return buf
    }

    private fun applyScaler(fitur: FloatArray, mean: FloatArray, scale: FloatArray): FloatArray {
        val out = FloatArray(fitur.size)
        val n   = min(fitur.size, min(mean.size, scale.size))
        for (i in 0 until n) {
            val s = if (scale[i] < 1e-9f) 1f else scale[i]
            out[i] = (fitur[i] - mean[i]) / s
        }
        return out
    }

    private fun scalerForGerakan(folder: String): Pair<FloatArray, FloatArray> = when (folder) {
        "Pukulan2"   -> Pair(PUKULAN2_MEAN,   PUKULAN2_SCALE)
        "Pukulan4"   -> Pair(PUKULAN4_MEAN,   PUKULAN4_SCALE)
        "Tangkisan1" -> Pair(TANGKISAN1_MEAN, TANGKISAN1_SCALE)
        "Tangkisan3" -> Pair(TANGKISAN3_MEAN, TANGKISAN3_SCALE)
        "Tendangan2" -> Pair(TENDANGAN2_MEAN, TENDANGAN2_SCALE)
        else         -> Pair(FloatArray(0),   FloatArray(0))
    }

    private fun tipeFromFolder(folder: String): String = when {
        folder.startsWith("Pukulan")   -> "pukulan"
        folder.startsWith("Tangkisan") -> "tangkisan"
        folder.startsWith("Tendangan") -> "tendangan"
        else                           -> "pukulan"
    }

    private fun tentukanKategori(skor: Double): String = when {
        skor >= 85 -> "Sangat Baik"
        skor >= 70 -> "Baik"
        skor >= 55 -> "Cukup"
        else       -> "Kurang"
    }

    private fun gagal(msg: String, durasiMs: Long) = HasilEvaluasi(
        idGerakan    = folderGerakan.lowercase(),
        labelGerakan = folderGerakan,
        skorTotal    = 0.0,
        kategori     = "Kurang",
        skorPerFitur = emptyMap(),
        feedbackList = listOf(msg),
        durasiMs     = durasiMs
    )

    private fun gagalDenganKonteks(
        pesan: String,
        durasiMs: Long,
        salah: Boolean,
        terdeteksi: String
    ) = HasilEvaluasi(
        idGerakan       = folderGerakan.lowercase(),
        labelGerakan    = folderGerakan,
        skorTotal       = 0.0,
        kategori        = "Kurang",
        skorPerFitur    = emptyMap(),
        feedbackList    = listOf(pesan),
        durasiMs        = durasiMs,
        gerakanSalah    = salah,
        gerakanTerdeteksi = terdeteksi
    )

    /**
     * Nama gerakan yang ramah untuk ditampilkan ke user.
     * Contoh: "Pukulan2Kanan" → "Pukulan 2 Kanan"
     *
     * CATATAN: Android tidak mendukung variable-length lookbehind regex
     * seperti `(?<=[A-Z]{2,})` → PatternSyntaxException di runtime.
     * Gunakan pemrosesan karakter manual sebagai gantinya.
     */
    private fun namaRamahGerakan(label: String): String {
        return sisipkanSpasiCamelCase(label)
    }

    /**
     * Sisipkan spasi pada batas CamelCase tanpa regex lookbehind.
     * Menangani: lowercase→Uppercase, UPPER→Uppercase, huruf→angka, angka→huruf.
     * Contoh: "Pukulan2Kanan" → "Pukulan 2 Kanan"
     *         "Tangkisan1Kiri" → "Tangkisan 1 Kiri"
     */
    private fun sisipkanSpasiCamelCase(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder()
        sb.append(input[0])
        for (i in 1 until input.length) {
            val prev = input[i - 1]
            val curr = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else '\u0000'

            val sisipSpasi = when {
                // lowercase → Uppercase: "nK" → "n K"
                prev.isLowerCase() && curr.isUpperCase() -> true
                // angka → huruf atau huruf → angka: "2K" → "2 K" / "n2" → "n 2"
                prev.isDigit() && curr.isLetter() -> true
                prev.isLetter() && curr.isDigit() -> true
                // UPPER UPPER lowercase: "NKa" → "N Ka" (batas akronim)
                prev.isUpperCase() && curr.isUpperCase() && next.isLowerCase() -> true
                else -> false
            }

            if (sisipSpasi) sb.append(' ')
            sb.append(curr)
        }
        return sb.toString()
    }

    /**
     * Saran spesifik per kategori gerakan agar user tahu apa yang harus diperbaiki.
     */
    private fun saranPerbaikanGerakan(folder: String): String = when {
        folder.startsWith("Pukulan")   -> "\n• Arahkan kepalan tangan ke depan sejajar bahu\n• Tekuk kuda-kuda dan posisikan kaki selebar bahu"
        folder.startsWith("Tangkisan") -> "\n• Angkat lengan untuk menahan/memblok serangan\n• Posisikan badan tegak dengan kuda-kuda stabil"
        folder.startsWith("Tendangan") -> "\n• Angkat lutut terlebih dahulu sebelum menendang\n• Jaga keseimbangan pada kaki tumpuan"
        else                           -> "\n• Ikuti instruksi gerakan yang ditampilkan"
    }
}
