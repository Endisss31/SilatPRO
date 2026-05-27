# 🥋 SilatPRO — Aplikasi Evaluasi Gerakan Pencak Silat Berbasis AI

> Aplikasi Android untuk belajar dan mengevaluasi gerakan teknik dasar Pencak Silat menggunakan Computer Vision dan Machine Learning secara real-time.

---

## 📋 Daftar Isi

- [Tentang Aplikasi](#tentang-aplikasi)
- [Fitur Utama](#fitur-utama)
- [Teknologi yang Digunakan](#teknologi-yang-digunakan)
- [Arsitektur Aplikasi](#arsitektur-aplikasi)
- [Struktur Proyek](#struktur-proyek)
- [Alur Kerja Aplikasi](#alur-kerja-aplikasi)
- [Penjelasan Setiap Komponen](#penjelasan-setiap-komponen)
- [Gerakan yang Didukung](#gerakan-yang-didukung)
- [Model AI & Pipeline ML](#model-ai--pipeline-ml)
- [Database & Riwayat](#database--riwayat)
- [Cara Build & Jalankan](#cara-build--jalankan)

---

## Tentang Aplikasi

**SilatPRO** adalah aplikasi Android yang memanfaatkan kamera HP untuk mendeteksi, mengklasifikasi, dan mengevaluasi gerakan teknik dasar Pencak Silat secara otomatis. Pengguna cukup berdiri di depan kamera, melakukan gerakan, dan aplikasi akan memberikan **skor**, **feedback**, dan **kategori penilaian** secara langsung.

- **Platform:** Android (minSdk 26 / Android 8.0+)
- **Bahasa:** Kotlin
- **Namespace:** `com.el.silatpro`

---

## Fitur Utama

| Fitur | Keterangan |
|-------|-----------|
| 🎥 **Mode Kamera Terbuka** | Deteksi gerakan secara real-time tanpa target tertentu. Cocok untuk eksplorasi. |
| 📊 **Mode Evaluasi Real-time** | Evaluasi gerakan spesifik dengan skor & feedback langsung saat bergerak. |
| 🎬 **Mode Kamera Rekam** | Rekam gerakan offline lalu evaluasi hasilnya — tidak perlu melihat layar saat bergerak. |
| 🦴 **Overlay Skeleton Pose** | Visualisasi 17 titik keypoint tubuh di atas kamera secara real-time. |
| 🔊 **Text-to-Speech Feedback** | Feedback perbaikan diucapkan secara lisan agar pengguna tidak perlu melihat layar. |
| 📚 **Galeri Gerakan** | Panduan visual dan deskripsi semua gerakan yang tersedia. |
| 📈 **Riwayat Latihan** | Semua sesi latihan tersimpan otomatis beserta skor, foto pose terbaik, dan feedback. |
| 🖼️ **Foto Pose Terbaik** | Saat skor tertinggi dicapai, foto pose + skeleton disimpan otomatis. |

---

## Teknologi yang Digunakan

### AI / Machine Learning
| Library | Versi | Fungsi |
|---------|-------|--------|
| **TensorFlow Lite** | 2.17.0 | Inferensi model YOLOv8-Pose & MLP di device |
| **TFLite GPU Delegate** | — | Akselerasi GPU (Adreno/Mali) |
| **NNAPI Delegate** | — | Akselerasi hardware (GPU/DSP/NPU) |
| **ML Kit Pose Detection** | — | Deteksi pose untuk visual overlay (lebih cepat) |

### Android Core
| Library | Fungsi |
|---------|--------|
| **CameraX** | Manajemen kamera (preview, analisis frame) |
| **Room** | Database lokal (riwayat latihan) |
| **ViewPager2** | Navigasi antar tab utama |
| **ViewBinding** | Binding layout XML ke kode |
| **Kotlin Coroutines** | Operasi async (background thread) |
| **Gson** | Parse/serialize JSON (model rule-based, riwayat) |

---

## Arsitektur Aplikasi

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│  ActivityMain → ViewPager2 → 4 Fragment          │
│  (Beranda | Galeri | Riwayat | Tentang)          │
└────────────────────┬────────────────────────────┘
                     │ FAB / Intent
┌────────────────────▼────────────────────────────┐
│              Kamera Activities                   │
│  ActivityKlasifikasiGerakan (Open Camera)        │
│  ActivityKameraEvaluasi (Mode Realtime)          │
│  ActivityKameraRekam (Mode Rekam)                │
└────────────────────┬────────────────────────────┘
                     │ CameraX ImageProxy
┌────────────────────▼────────────────────────────┐
│               AI Pipeline                        │
│                                                  │
│  Frame Kamera (Bitmap)                           │
│       ↓ [Dual Track]                            │
│  ┌─────────────┐    ┌──────────────────┐        │
│  │ YOLO Track  │    │  ML Kit Track    │        │
│  │ (500ms)     │    │  (setiap frame)  │        │
│  └──────┬──────┘    └────────┬─────────┘        │
│         │                   │                   │
│  PendeteksiPose         PendeteksiPoseMLKit     │
│  (YOLOv8x-pose)        (untuk overlay cepat)   │
│         ↓                   ↓                   │
│  PenstabilPose (One Euro Filter)                │
│         ↓                   ↓                   │
│  [Evaluasi Skor]      [Gambar Skeleton]         │
│  PengekstrakFitur                               │
│         ↓                                       │
│  MesinKlasifikasi / PenilaiGerakan              │
│         ↓                                       │
│  Skor + Feedback → UI                           │
└─────────────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              Data Layer                          │
│  Room Database (BasisDataAplikasi)              │
│  EntitasRiwayatLatihan (skor, foto, feedback)   │
└─────────────────────────────────────────────────┘
```

---

## Struktur Proyek

```
app/src/main/java/com/el/silatpro/
│
├── 📱 Activities & Dialogs (root package)
│   ├── ActivityMain.kt               ← Halaman utama (ViewPager2 + Bottom Nav)
│   ├── ActivitySplash.kt             ← Splash screen animasi
│   ├── ActivityOpenSplash.kt         ← Splash pertama kali buka
│   ├── ActivityDetailGerakan.kt      ← Halaman detail + panduan gerakan
│   ├── ActivityKlasifikasiGerakan.kt ← Kamera terbuka (Open Camera mode)
│   ├── ActivityKameraEvaluasi.kt     ← Evaluasi real-time dengan skor
│   ├── ActivityKameraRekam.kt        ← Mode rekam offline
│   ├── ActivityHasilEvaluasi.kt      ← Tampilkan hasil & riwayat latihan
│   ├── ActivityTentang.kt            ← Halaman tentang aplikasi
│   ├── DialogKetentuanKamera.kt      ← Popup syarat penggunaan kamera
│   ├── DialogPilihMode.kt            ← Popup pilih mode (rekam/realtime)
│   └── PengaturStatusBar.kt          ← Utilitas status bar
│
├── 🤖 ai/ — Engine AI
│   ├── MesinKlasifikasi.kt           ← Klasifikasi global (Open Camera)
│   ├── PendeteksiPose.kt             ← Deteksi pose YOLOv8x-Pose TFLite
│   ├── PendeteksiPoseMLKit.kt        ← Deteksi pose ML Kit (untuk overlay)
│   ├── PengekstrakFitur.kt           ← Ekstrak 34/42 fitur dari pose
│   ├── PengevaluasiRuleBase.kt       ← Evaluasi berbasis aturan (rule-based)
│   ├── PenilaiGerakan.kt             ← Pipeline evaluasi lengkap (ML + rule)
│   └── PenstabilPose.kt              ← Smoothing pose (One Euro Filter)
│
├── 💾 data/ — Database
│   ├── BasisDataAplikasi.kt          ← Room Database singleton
│   ├── DaoRiwayatLatihan.kt          ← Query DAO (simpan/ambil riwayat)
│   └── EntitasRiwayatLatihan.kt      ← Entitas tabel riwayat_latihan
│
├── 📦 model/ — Data Models
│   ├── DataPose.kt                   ← Data class pose (17 keypoint)
│   ├── GlobalScaler.kt               ← Mean & scale StandardScaler global
│   ├── HasilEvaluasi.kt              ← Result data class evaluasi
│   ├── ModelGerakan.kt               ← Model data gerakan
│   └── ScalerEvaluasi.kt             ← Scaler per gerakan spesifik
│
└── 🎨 ui/ — Fragments & Adapters
    ├── beranda/
    │   ├── FragmenBeranda.kt          ← Tab Beranda (statistik + navigasi)
    │   ├── AdapterGerakan.kt          ← RecyclerView daftar gerakan
    │   └── AdapterVariasi.kt          ← RecyclerView variasi gerakan
    ├── galeri/
    │   └── FragmenGaleri.kt           ← Tab Galeri (daftar semua gerakan)
    ├── kamera/
    │   ├── ManajerKamera.kt           ← Manajemen CameraX (resolusi, eksposur)
    │   └── TampilanOverlayPose.kt     ← Custom View gambar skeleton
    ├── riwayat/
    │   ├── FragmenRiwayat.kt          ← Tab Riwayat latihan
    │   ├── AdapterRiwayat.kt          ← List riwayat sesi
    │   ├── AdapterDetailFitur.kt      ← Skor per fitur (RecyclerView)
    │   └── AdapterFeedback.kt         ← List feedback teks
    ├── onboarding/                    ← Layar onboarding (pertama buka)
    └── tentang/
        └── FragmenTentang.kt          ← Tab tentang aplikasi
```

---

## Alur Kerja Aplikasi

### 1. Alur Utama (Evaluasi Gerakan)

```
Splash Screen
    ↓
ActivityMain (Tab Beranda)
    ↓ [Klik kartu gerakan di Galeri]
ActivityDetailGerakan
    ↓ [Klik FAB "Mulai Latihan"]
DialogPilihMode → [Mode Realtime atau Mode Rekam]
    ↓
ActivityKameraEvaluasi / ActivityKameraRekam
    ↓ [Proses setiap frame kamera]
    │
    ├── ML Kit (cepat) → Gambar skeleton di overlay
    │
    └── YOLOv8x (tiap 500ms) → Evaluasi skor
            ↓
        PengekstrakFitur (34 fitur keypoint)
            ↓
        PenilaiGerakan
        ├── Step 1: Global MLP → Deteksi jenis + sisi gerakan
        ├── Step 2: Evaluator MLP Spesifik → Skor Benar/Salah
        └── Step 3: Rule-based Feedback → Feedback per bagian tubuh
            ↓
        Skor Gabungan (60% rule-based + 40% ML)
            ↓
    [Klik "Selesai"]
        ↓
    Simpan ke Room Database
        ↓
ActivityHasilEvaluasi (Skor, Foto, Feedback)
```

### 2. Alur Open Camera (Kamera Terbuka)

```
ActivityMain → [FAB Kamera tengah]
    ↓
ActivityKlasifikasiGerakan
    ↓ [Tiap 1 detik]
YOLOv8x → PengekstrakFitur → MesinKlasifikasi
    ↓
Tampilkan label gerakan + confidence (%)
```

---

## Penjelasan Setiap Komponen

### 🎯 `PendeteksiPose.kt`
Deteksi pose menggunakan model **YOLOv8x-Pose** (float16 TFLite).

**Flow:**
1. Bitmap dari kamera → **Letterbox** ke 640×640
2. Encode pixel ke ByteBuffer (normalisasi 0–1)
3. Jalankan inferensi → output `[1, 56, 8400]`
4. Pilih deteksi dengan confidence tertinggi
5. Ekstrak 17 keypoints COCO + reverse letterbox ke koordinat asli
6. Return `DataPose` (17 `TitikTubuh` dengan x, y, confidence)

**Akselerasi Hardware (prioritas):**
1. NNAPI Delegate (GPU/DSP/NPU) — untuk HP modern
2. GPU Delegate (Adreno/Mali) — fallback
3. XNNPACK CPU — selalu aktif sebagai optimasi

---

### 🤖 `MesinKlasifikasi.kt`
Klasifikasi gerakan untuk **Mode Open Camera**.

**Flow:**
1. Terima `FloatArray(34)` — 17 keypoints (x, y) ternormalisasi
2. Terapkan **StandardScaler** (`GlobalScaler.mean`, `GlobalScaler.scale`)
3. Jalankan model `global_mlp_yolov8x_float16.tflite`
4. Ambil kelas dengan probabilitas tertinggi
5. Cek threshold 70% → return label + probabilitas

**Output:** `Pair<String, Double>?` (label, probabilitas) atau null jika di bawah threshold.

---

### 🏆 `PenilaiGerakan.kt`
Pipeline evaluasi gerakan untuk **Mode Evaluasi** — ini adalah komponen utama penilaian.

**Pipeline 3 Langkah:**

```
Step 1: Global MLP (34 fitur, GlobalScaler)
    → Deteksi jenis gerakan (Pukulan2Kanan, Tangkisan1Kiri, dll.)
    → Threshold: 60%

Step 2: Evaluator MLP Spesifik (34 fitur, ScalerEvaluasi per gerakan)
    → 2 output: probabilitas Benar vs Salah
    → Threshold: 70%

Step 3: Rule-based (PengevaluasiRuleBase)
    → Baca model JSON dari assets/movement_models/
    → Hitung fitur geometri (sudut sendi, jarak kaki/tangan)
    → Bandingkan terhadap target ± toleransi
    → Skor per fitur: 100 (dalam toleransi) → turun linear

Skor Akhir = 60% Rule-based + 40% ML
```

**Kategori Skor:**
| Skor | Kategori |
|------|----------|
| ≥ 85 | Sangat Baik |
| ≥ 70 | Baik |
| ≥ 55 | Cukup |
| < 55 | Kurang |

---

### 📐 `PengekstrakFitur.kt`
Mengekstrak fitur dari `DataPose` untuk input model ML.

**Normalisasi (sama persis dengan training):**
```
shoulder_center = (left_shoulder + right_shoulder) / 2
hip_center      = (left_hip + right_hip) / 2
body_center     = (shoulder_center + hip_center) / 2
body_scale      = max(lebar_bahu, lebar_pinggul)
normalized_kp   = (keypoint - body_center) / body_scale
```

**Output:**
- `ekstrak()` → `FloatArray(42)` — 34 keypoints + 8 fitur geometri
- `ekstrak39()` → `FloatArray(39)` — 34 keypoints + 5 fitur rule-based

---

### 🔧 `PengevaluasiRuleBase.kt`
Evaluasi berbasis aturan yang melengkapi model ML.

**Cara kerja:**
1. Baca JSON dari `assets/movement_models/<id>.json`
2. Hitung fitur geometri dari `DataPose`:
   - Sudut lengan kiri/kanan (bahu–siku–pergelangan)
   - Sudut kaki kiri/kanan (pinggul–lutut–pergelangan kaki)
   - Sudut pinggang kiri/kanan (bahu–pinggul–lutut)
   - Jarak kaki (ternormalisasi terhadap lebar bahu)
   - Jarak tangan (ternormalisasi terhadap lebar bahu)
3. Bandingkan setiap fitur vs target ± toleransi dari JSON
4. Hitung skor per fitur (linear decay)
5. Skor total = rata-rata semua fitur

**Feedback teks:** Low message / High message / null (jika sudah dalam toleransi)

---

### 📸 `PendeteksiPoseMLKit.kt`
Deteksi pose menggunakan **Google ML Kit** — digunakan khusus untuk **menggambar skeleton** di overlay, karena lebih cepat dari YOLO.

---

### 🌊 `PenstabilPose.kt`
Stabilisasi koordinat keypoint menggunakan **One Euro Filter**.

**Cara kerja:** Filter adaptif yang menyeimbangkan:
- **Kehalusan** saat pose diam (mengurangi jitter/gemetar)
- **Responsivitas** saat pose bergerak cepat

Digunakan dua instance terpisah: satu untuk YOLO, satu untuk ML Kit.

---

### 📷 `ManajerKamera.kt`
Manajemen kamera menggunakan **CameraX**.

**Fitur:**
- Pemilihan resolusi otomatis + fallback
- Ganti kamera depan/belakang
- Kontrol eksposur
- Mode performa: `LIVE` (FPS tinggi) atau `QUALITY` (resolusi lebih baik)

---

### 🎨 `TampilanOverlayPose.kt`
Custom View yang menggambar skeleton di atas preview kamera.

**Fitur:**
- Gambar 17 titik keypoint (lingkaran berwarna)
- Gambar tulang/koneksi antar keypoint (garis)
- Support mode kamera depan (flip horizontal)
- Support dua mode: fillCenter dan fitXY

---

### 💾 `BasisDataAplikasi.kt`
Room Database singleton untuk menyimpan riwayat latihan.

**Tabel:** `riwayat_latihan`

| Kolom | Tipe | Keterangan |
|-------|------|-----------|
| `id` | Int (PK) | Auto-increment |
| `idGerakan` | String | Path asset model |
| `labelGerakan` | String | Nama gerakan |
| `skor` | Double | Skor total (0–100) |
| `kategori` | String | Sangat Baik / Baik / Cukup / Kurang |
| `skorPerFitur` | String (JSON) | Map skor per fitur geometri |
| `feedback` | String (JSON) | List feedback teks |
| `durasiMs` | Long | Durasi sesi dalam ms |
| `pathFoto` | String? | Path foto pose terbaik |

---

## Gerakan yang Didukung

| ID Gerakan | Nama | Deskripsi Singkat |
|-----------|------|------------------|
| `pukulan_2` | Pukulan 2 | Pukulan lurus ke samping sejajar bahu |
| `pukulan_4` | Pukulan 4 | Pukulan menyilang ke arah tulang rusuk |
| `tangkisan_1` | Tangkisan 1 | Tangkisan tangan terbuka (posisi hormat) |
| `tangkisan_3` | Tangkisan 3 | Tangkisan ke atas melindungi kepala |
| `tendangan_2` | Tendangan 2 | Tendangan ke arah dada lawan |

Setiap gerakan didukung untuk **sisi kanan dan kiri** (terdeteksi otomatis oleh model global).

---

## Model AI & Pipeline ML

### Asset Model di `assets/`

```
assets/
├── yolov8x-pose_float16.tflite       ← Model deteksi pose (semua mode)
│
├── GlobalMovement/
│   ├── global_mlp_yolov8x_float16.tflite  ← Classifier 10 kelas global
│   └── global_labels.txt                   ← Label 10 kelas
│
├── Model/
│   ├── Pukulan2/pukulan2_float16.tflite
│   ├── Pukulan4/pukulan4_float16.tflite
│   ├── Tangkisan1/tangkisan1_float16.tflite
│   ├── Tangkisan3/tangkisan3_float16.tflite
│   └── Tendangan2/tendangan2_float16.tflite
│
└── movement_models/
    ├── pukulan_2_kanan.json
    ├── pukulan_2_kiri.json
    ├── pukulan_4_kanan.json
    ├── ... (1 file per gerakan per sisi)
    └── movement_model_index.json
```

### 10 Kelas Global Model

```
PUKULAN2KANAN   PUKULAN2KIRI
PUKULAN4KANAN   PUKULAN4KIRI
TANGKISAN1KANAN TANGKISAN1KIRI
TANGKISAN3KANAN TANGKISAN3KIRI
TENDANGAN2KANAN TENDANGAN2KIRI
```

### Input/Output Model

| Model | Input | Output |
|-------|-------|--------|
| YOLOv8x-Pose | `[1, 640, 640, 3]` Float32 | `[1, 56, 8400]` Float32 |
| Global MLP | `FloatArray(34)` (scaled) | `FloatArray(10)` softmax |
| Evaluator MLP | `FloatArray(34)` (scaled) | `FloatArray(2)` [benar, salah] |

---

## Navigasi Antar Layar

```
ActivityOpenSplash (intro)
    ↓
ActivitySplash (animasi logo)
    ↓
ActivityMain ─┬─ [Tab Beranda]  FragmenBeranda
              ├─ [Tab Galeri]   FragmenGaleri → ActivityDetailGerakan
              ├─ [FAB Kamera]   ActivityKlasifikasiGerakan (Open Camera)
              ├─ [Tab Riwayat]  FragmenRiwayat → ActivityHasilEvaluasi
              └─ [Tab Tentang]  FragmenTentang

ActivityDetailGerakan
    └─ [FAB Mulai] → DialogPilihMode
                         ├─ MODE_REALTIME → ActivityKameraEvaluasi
                         │                      → ActivityHasilEvaluasi
                         └─ MODE_REKAM    → ActivityKameraRekam
                                               → ActivityHasilEvaluasi
```

---

## Dual-Track Processing (Teknis Penting)

Aplikasi menggunakan **dua jalur pemrosesan paralel** per frame:

```
Frame Kamera (ImageProxy)
        │
        ├─── [Track 1] ML Kit (setiap frame, ~30fps)
        │        └─ Deteksi pose cepat
        │        └─ Gambar skeleton di overlay
        │        └─ Langsung close frame (tidak blocking)
        │
        └─── [Track 2] YOLO (tiap 500ms - evaluasi / 1000ms - klasifikasi)
                 └─ Salin Bitmap dari frame
                 └─ Rotate sesuai orientasi
                 └─ YOLOv8x inferensi (background thread)
                 └─ Ekstrak fitur → Evaluasi skor
                 └─ Update UI (Main thread)
```

Desain ini mencegah **drop frame** dan **SIGSEGV** (crash native) karena:
- ML Kit memproses frame asli → frame langsung di-close
- YOLO menerima **salinan Bitmap** → tidak bergantung pada frame buffer kamera

---

## Cara Build & Jalankan

### Prasyarat
- Android Studio Hedgehog atau lebih baru
- JDK 17
- Android SDK 35
- Device/Emulator Android 8.0+ (API 26+)

### Langkah Build

```bash
# Clone atau buka project
# di Android Studio:

1. File → Open → pilih folder SilatPRObckp/
2. Tunggu Gradle sync selesai
3. Pastikan device terhubung (USB/Wireless)
4. Run → Run 'app'
```

### Izin yang Dibutuhkan

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Izin kamera diminta secara runtime saat pertama membuka fitur kamera.

---

## Konvensi Penamaan (Bahasa Indonesia)

Seluruh kode ditulis dengan **konvensi nama Bahasa Indonesia** agar mudah dipahami:

| Kode | Arti |
|------|------|
| `pendeteksi` | detector |
| `pengekstrak` | extractor |
| `penstabil` | stabilizer |
| `penilai` | evaluator/scorer |
| `mesin` | engine |
| `manajer` | manager |
| `gerakan` | movement |
| `pose` | pose |
| `fitur` | feature |
| `skor` | score |
| `riwayat` | history |
| `beranda` | home |
| `galeri` | gallery |

---

*Dokumentasi ini dibuat otomatis berdasarkan analisis source code SilatPRO.*
