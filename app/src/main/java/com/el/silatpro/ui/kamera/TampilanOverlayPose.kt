package com.el.silatpro.ui.kamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import kotlin.math.sqrt

/**
 * Custom View untuk menggambar skeleton (rangka tubuh) di atas preview kamera.
 */
class TampilanOverlayPose @JvmOverloads constructor(
    konteks: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(konteks, attrs, defStyleAttr) {

    private var dataPose: DataPose? = null
    private var lebarGambar: Float = 1f
    private var tinggiGambar: Float = 1f
    private var apakahKameraDepan: Boolean = false
    /**
     * true  = fillCenter (layar penuh, crop/zoom) — untuk kamera klasifikasi 9:16
     * false = fitCenter  (letterbox, tidak crop)  — untuk kamera evaluasi 4:3
     */
    private var gunakanFillCenter: Boolean = false

    companion object {
        private const val AMBANG_TITIK_TERLIHAT = 0.35f
        private const val AMBANG_TITIK_WAJAH = 0.55f
        private const val MAKS_JARAK_TELINGA_DARI_MATA = 0.85f
    }

    init {
        setWillNotDraw(false)
    }

    fun setKameraDepan(isDepan: Boolean) {
        apakahKameraDepan = isDepan
    }

    /** Aktifkan mode fillCenter agar skeleton mengikuti area crop kamera (bukan letterbox) */
    fun setModeFillCenter(aktif: Boolean) {
        gunakanFillCenter = aktif
    }

    // Cat untuk menggambar
    private val catGaris = Paint().apply {
        color = Color.parseColor("#0090FF") // Biru Utama
        strokeWidth = 10f // Lebih tebal
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val catTitik = Paint().apply {
        color = Color.parseColor("#FFFFFF") // Putih untuk titik
        strokeWidth = 16f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val catTitikRendah = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        strokeWidth = 10f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Koneksi skeleton (pasangan indeks keypoint)
    private val koneksiSkeleton = listOf(
        // Wajah
        Pair(TitikTubuh.MATA_KIRI, TitikTubuh.HIDUNG),
        Pair(TitikTubuh.MATA_KANAN, TitikTubuh.HIDUNG),
        Pair(TitikTubuh.MATA_KIRI, TitikTubuh.TELINGA_KIRI),
        Pair(TitikTubuh.MATA_KANAN, TitikTubuh.TELINGA_KANAN),
        // Tubuh atas
        Pair(TitikTubuh.BAHU_KIRI, TitikTubuh.BAHU_KANAN),
        Pair(TitikTubuh.BAHU_KIRI, TitikTubuh.SIKU_KIRI),
        Pair(TitikTubuh.SIKU_KIRI, TitikTubuh.PERGELANGAN_KIRI),
        Pair(TitikTubuh.BAHU_KANAN, TitikTubuh.SIKU_KANAN),
        Pair(TitikTubuh.SIKU_KANAN, TitikTubuh.PERGELANGAN_KANAN),
        // Torso
        Pair(TitikTubuh.BAHU_KIRI, TitikTubuh.PINGGUL_KIRI),
        Pair(TitikTubuh.BAHU_KANAN, TitikTubuh.PINGGUL_KANAN),
        Pair(TitikTubuh.PINGGUL_KIRI, TitikTubuh.PINGGUL_KANAN),
        // Tubuh bawah
        Pair(TitikTubuh.PINGGUL_KIRI, TitikTubuh.LUTUT_KIRI),
        Pair(TitikTubuh.LUTUT_KIRI, TitikTubuh.PERGELANGAN_KAKI_KIRI),
        Pair(TitikTubuh.PINGGUL_KANAN, TitikTubuh.LUTUT_KANAN),
        Pair(TitikTubuh.LUTUT_KANAN, TitikTubuh.PERGELANGAN_KAKI_KANAN)
    )

    /**
     * Perbarui data pose dan gambar ulang.
     */
    fun perbaruiPose(pose: DataPose?, lebarGbr: Float, tinggiGbr: Float) {
        dataPose = pose
        lebarGambar = lebarGbr
        tinggiGambar = tinggiGbr
        postInvalidate()
    }

    fun bersihkan() {
        dataPose = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pose = dataPose ?: return

        // ── Pilih mode scaling sesuai PreviewView ────────────────────────────────
        val skalaX = width.toFloat() / lebarGambar
        val skalaY = height.toFloat() / tinggiGambar

        val skala: Float
        val offsetX: Float
        val offsetY: Float

        if (gunakanFillCenter) {
            // FILL-CENTER: ambil skala TERBESAR agar gambar mengisi penuh (ada crop di tepi)
            // Selaras dengan scaleType="fillCenter" di PreviewView kamera klasifikasi 9:16
            skala = maxOf(skalaX, skalaY)
            offsetX = (width  - lebarGambar * skala) / 2f
            offsetY = (height - tinggiGambar * skala) / 2f
        } else {
            // FIT-CENTER: ambil skala TERKECIL agar semua gambar muat (letterbox hitam di tepi)
            // Selaras dengan scaleType="fitCenter" di PreviewView kamera evaluasi 4:3
            skala = minOf(skalaX, skalaY)
            offsetX = (width  - lebarGambar * skala) / 2f
            offsetY = (height - tinggiGambar * skala) / 2f
        }

        // ── Gambar garis skeleton ────────────────────────────────────────────
        for ((idx1, idx2) in koneksiSkeleton) {
            val t1 = pose.ambilTitik(idx1) ?: continue
            val t2 = pose.ambilTitik(idx2) ?: continue
            if (!bolehGambarTitik(pose, t1) || !bolehGambarTitik(pose, t2)) continue

            var x1 = t1.x * skala + offsetX
            var x2 = t2.x * skala + offsetX
            val y1 = t1.y * skala + offsetY
            val y2 = t2.y * skala + offsetY

            // Balik X jika kamera depan (mirrored)
            if (apakahKameraDepan) {
                val batasKanan = width.toFloat() - offsetX
                x1 = batasKanan - (x1 - offsetX)
                x2 = batasKanan - (x2 - offsetX)
            }

            canvas.drawLine(x1, y1, x2, y2, catGaris)
        }

        // ── Gambar titik-titik keypoint ──────────────────────────────────────
        for (titik in pose.titikTubuh) {
            if (!bolehGambarTitik(pose, titik)) continue

            var x = titik.x * skala + offsetX
            val y = titik.y * skala + offsetY

            // Balik X jika kamera depan
            if (apakahKameraDepan) {
                val batasKanan = width.toFloat() - offsetX
                x = batasKanan - (x - offsetX)
            }

            val cat = if (titik.konfiden > 0.4f) catTitik else catTitikRendah
            val radius = if (titik.konfiden > 0.4f) 10f else 6f
            canvas.drawCircle(x, y, radius, cat)
        }
    }

    private fun bolehGambarTitik(pose: DataPose, titik: TitikTubuh): Boolean {
        if (titik.konfiden < AMBANG_TITIK_TERLIHAT) return false

        return when (titik.indeks) {
            TitikTubuh.TELINGA_KIRI,
            TitikTubuh.TELINGA_KANAN -> telingaMasukAkal(pose, titik)
            TitikTubuh.MATA_KIRI,
            TitikTubuh.MATA_KANAN,
            TitikTubuh.HIDUNG -> titik.konfiden >= AMBANG_TITIK_WAJAH
            else -> true
        }
    }

    private fun telingaMasukAkal(pose: DataPose, telinga: TitikTubuh): Boolean {
        if (telinga.konfiden < AMBANG_TITIK_WAJAH) return false

        val mata = when (telinga.indeks) {
            TitikTubuh.TELINGA_KIRI -> pose.ambilTitik(TitikTubuh.MATA_KIRI)
            TitikTubuh.TELINGA_KANAN -> pose.ambilTitik(TitikTubuh.MATA_KANAN)
            else -> null
        } ?: return false

        if (mata.konfiden < AMBANG_TITIK_WAJAH) return false

        val bahuKiri = pose.ambilTitik(TitikTubuh.BAHU_KIRI) ?: return false
        val bahuKanan = pose.ambilTitik(TitikTubuh.BAHU_KANAN) ?: return false
        val lebarBahu = jarak(bahuKiri, bahuKanan)
        if (lebarBahu <= 0f) return false

        return jarak(telinga, mata) <= lebarBahu * MAKS_JARAK_TELINGA_DARI_MATA
    }

    private fun jarak(a: TitikTubuh, b: TitikTubuh): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
