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
| **TensorFlow Lite** | 2.17.0 | Inferensi model YOLOv8n-Pose & MLP Global di device |
| **XNNPACK** | — | Akselerasi CPU (aktif default, stabil di semua device) |
| **ML Kit Pose Detection** | — | Deteksi pose untuk visual overlay skeleton (lebih cepat) |

### Android Core
| Library | Fungsi |
|---------|--------|
| **CameraX** | Manajemen kamera (preview, analisis frame) |
| **Room** | Database lokal (riwayat latihan) |
| **ViewPager2** | Navigasi antar tab utama |
| **ViewBinding** | Binding layout XML ke kode |
| **Kotlin Coroutines** | Operasi async (background thread) |
| **Gson** | Parse/serialize JSON (rule-based model, riwayat) |

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
│  ActivityKameraEvaluasi     (Mode Evaluasi)      │
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
│  (YOLOv8n-Pose f16)    (untuk overlay cepat)   │
│         ↓                   ↓                   │
│  PenstabilPose (One Euro Filter)                │
│         ↓                   ↓                   │
│  [Evaluasi Skor]      [Gambar Skeleton]         │
│  Normalizer (body-relative)                     │
│         ↓                                       │
│  MLPClassifier / MesinKlasifikasi               │
│  (Global MLP — 10 kelas)                        │
│         ↓                                       │
│  PengevaluasiRuleBase (JSON rule-based)         │
│         ↓                                       │
│  Skor + Feedback → UI + TTS                     │
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
│   ├── ActivityDetailGerakan.kt      ← Halaman detail + panduan gerakan (langsung ke Evaluasi)
│   ├── ActivityKlasifikasiGerakan.kt ← Kamera terbuka (Open Camera mode)
│   ├── ActivityKameraEvaluasi.kt     ← Evaluasi real-time dengan skor + TTS
│   ├── ActivityHasilEvaluasi.kt      ← Tampilkan hasil & riwayat latihan
│   ├── ActivityTentang.kt            ← Halaman tentang aplikasi
│   ├── DialogKetentuanKamera.kt      ← Popup syarat penggunaan kamera
│   ├── ActivityKameraRekam.kt        ← ⚠ Tidak dipakai (orphaned, aman dihapus)
│   ├── DialogPilihMode.kt            ← ⚠ Tidak dipakai (orphaned, aman dihapus)
│   └── PengaturStatusBar.kt          ← Utilitas status bar
│
├── 🤖 ai/ — Engine AI
│   ├── MLPClassifier.kt              ← Inferensi model MLP Global (10 kelas)
│   ├── MesinKlasifikasi.kt           ← Wrapper klasifikasi untuk Open Camera
│   ├── Normalizer.kt                 ← Body-relative normalization + StandardScaler
│   ├── Letterbox.kt                  ← Letterbox resize Bitmap untuk input YOLO
│   ├── PendeteksiPose.kt             ← Deteksi pose YOLOv8n-Pose TFLite
│   ├── PendeteksiPoseMLKit.kt        ← Deteksi pose ML Kit (untuk overlay)
│   ├── PengekstrakFitur.kt           ← Ekstrak fitur geometri dari DataPose
│   ├── PengevaluasiRuleBase.kt       ← Evaluasi berbasis aturan (rule-based JSON)
│   ├── PenilaiGerakan.kt             ← Pipeline evaluasi lengkap (MLP global + rule)
│   └── PenstabilPose.kt              ← Smoothing pose (One Euro Filter)
│
├── 💾 data/ — Database
│   ├── BasisDataAplikasi.kt          ← Room Database singleton
│   ├── DaoRiwayatLatihan.kt          ← Query DAO (simpan/ambil riwayat)
│   └── EntitasRiwayatLatihan.kt      ← Entitas tabel riwayat_latihan
│
├── 📦 model/ — Data Models
│   ├── DataPose.kt                   ← Data class pose (17 keypoint)
│   ├── HasilEvaluasi.kt              ← Result data class evaluasi
│   ├── ModelGerakan.kt               ← Model data gerakan (untuk Galeri)
│   ├── ScalerLabelGlobal.kt          ← Mean/scale StandardScaler + label 10 kelas
│   └── ScalerEvaluasi.kt             ← Scaler per gerakan (legacy, belum dipakai)
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
    ↓ [Klik FAB "Mulai Latihan"] — langsung ke Evaluasi (tanpa dialog pilih mode)
    ↓
ActivityKameraEvaluasi
    ↓ [Proses setiap frame kamera]
    │
    ├── ML Kit (tiap frame) → Gambar skeleton di overlay
    │
    └── YOLOv8n-Pose (tiap 500ms) → Evaluasi skor
            ↓
        Normalizer (body-relative, 34 fitur)
            ↓
        PenilaiGerakan
        ├── Step 1: Apply StandardScaler (ScalerLabelGlobal)
        ├── Step 2: MLPClassifier Global → 10 kelas (70% threshold)
        ├── Step 3: Validasi gerakan vs target user
        ├── Step 4: Identifikasi sisi (kanan/kiri dari label)
        └── Step 5: PengevaluasiRuleBase → Skor + Feedback per fitur
            ↓
        Skor Total (rule-based) + Feedback TTS
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
YOLOv8n-Pose → Normalizer → MesinKlasifikasi → MLPClassifier
    ↓
Tampilkan label gerakan + confidence (%)
```

---

## Penjelasan Setiap Komponen

### 🎯 `PendeteksiPose.kt`
Deteksi pose menggunakan model **YOLOv8n-Pose** (float16 TFLite) — lebih ringan dan cepat.

**Flow:**
1. Bitmap dari kamera → **Letterbox** ke 640×640
2. Encode pixel ke ByteBuffer (normalisasi 0–1)
3. Jalankan inferensi → output `[1, 56, 8400]`
4. Pilih deteksi dengan confidence tertinggi (threshold 0.30)
5. Ekstrak 17 keypoints COCO + reverse letterbox ke koordinat asli
6. Return `DataPose` (17 `TitikTubuh` dengan x, y, confidence)

**Akselerasi Hardware:**
- XNNPACK CPU — stabil dan digunakan di semua device
- (NNAPI dinonaktifkan karena menyebabkan crash di beberapa device OPPO/Realme)

---

### 🔄 `Normalizer.kt`
Normalisasi fitur dari `DataPose` sebelum dimasukkan ke MLP.

**Normalisasi body-relative:**
```
center = midpoint(left_hip, right_hip)
scale  = shoulder_width  (fallback: hip_width)
x_norm = (x - centerX) / scale
y_norm = (y - centerY) / scale
```

**Fungsi:**
- `bodyRelativeOnly(pose)` → `FloatArray(34)` — 17 keypoints (x,y) ternormalisasi, untuk input MLP Global
- `normalize(pose)` → `FloatArray(34)` — body-relative + StandardScaler (siap untuk inferensi)

---

### 🤖 `MLPClassifier.kt`
Klasifikasi gerakan global menggunakan model `GlobalMovement/mlp_global_v8n.tflite`.

**Flow:**
1. Terima `FloatArray(34)` — sudah di-scale
2. Run inference → output 10 probabilitas softmax
3. Argmax → ambil label dengan probabilitas tertinggi
4. Threshold 70% → return label + probabilitas, atau "Gerakan tidak dikenali"

**Output:** `MLPClassifier.Result(label, confidence, allProbs)`

---

### 🏆 `PenilaiGerakan.kt`
Pipeline evaluasi gerakan untuk **Mode Evaluasi (Realtime & Rekam)**.

**Pipeline Lengkap:**

```
Step 1: Body-relative normalization (Normalizer)
    → 34 fitur dari pose

Step 2: StandardScaler → MLP Global Inference
    → Deteksi jenis + sisi gerakan (10 kelas)
    → Threshold: 70%

Step 3: Validasi gerakan vs target user
    → Jika tidak cocok → TTS "Terdeteksi X, lakukan Y"

Step 4: Identifikasi sisi (kanan/kiri dari label)
    → Pilih JSON rule: "<gerakan><sisi>_rule_reference.json"

Step 5: PengevaluasiRuleBase
    → Evaluasi pose vs aturan geometri dari JSON
    → Feedback per bagian tubuh
```

**Kategori Skor:**
| Skor | Kategori |
|------|----------|
| ≥ 85 | Sangat Baik |
| ≥ 70 | Baik |
| ≥ 55 | Cukup |
| < 55 | Kurang |

---

### 🏋️ `MesinKlasifikasi.kt`
Wrapper `MLPClassifier` untuk **Mode Open Camera**.

**Flow:**
- `DataPose` → `Normalizer.normalize()` (body-relative + scaler) → `MLPClassifier.classify()`
- Return `Pair<String, Double>` (label, confidence)

---

### 📐 `PengekstrakFitur.kt`
Mengekstrak fitur geometri tambahan dari `DataPose`.

> ⚠ Saat ini digunakan oleh `ActivityKameraRekam` yang sudah tidak aktif dari UI. File ini dapat dievaluasi untuk dihapus bersama `ActivityKameraRekam.kt`.

**Output:**
- `ekstrak()` → `FloatArray(42)` — 34 keypoints + 8 fitur geometri
- `ekstrak39()` → `FloatArray(39)` — 34 keypoints + 5 fitur rule-based

---

### 🔧 `PengevaluasiRuleBase.kt`
Evaluasi berbasis aturan yang melengkapi model ML.

**Cara kerja:**
1. Baca JSON dari `assets/movement_models/<nama>_rule_reference.json`
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
| `idGerakan` | String | Grup ID gerakan (mis. "pukulan_2") |
| `labelGerakan` | String | Nama gerakan (mis. "Pukulan 2 Kanan") |
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
| `tangkisan_2` | Tangkisan 2 | Tangkisan silang ke arah samping |
| `tangkisan_3` | Tangkisan 3 | Tangkisan ke atas melindungi kepala |

Setiap gerakan didukung untuk **sisi kanan dan kiri** (terdeteksi otomatis oleh model global).

---

## Model AI & Pipeline ML

### Asset Model di `assets/`

```
assets/
├── yolov8n_pose_float16.tflite       ← Model deteksi pose YOLO (semua mode)
├── yolov8n_pose_float32.tflite       ← ⚠ Tidak dipakai (bisa dihapus)
│
├── GlobalMovement/
│   ├── mlp_global_v8n.tflite         ← Classifier 10 kelas global ✅ AKTIF
│   ├── global_labels.txt             ← ⚠ Tidak dibaca kode (label ada di ScalerLabelGlobal.kt)
│   └── labels_global.txt             ← ⚠ Tidak dibaca kode (duplikat global_labels.txt)
│
├── movement_model_index.json         ← Index gerakan + mapping rule JSON
├── silat_classification_model.json   ← ⚠ Tidak dibaca kode manapun
│
└── movement_models/
    ├── pukulan2kanan_rule_reference.json
    ├── pukulan2kiri_rule_reference.json
    ├── pukulan4kanan_rule_reference.json
    ├── pukulan4kiri_rule_reference.json
    ├── tangkisan1kanan_rule_reference.json
    ├── tangkisan1kiri_rule_reference.json
    ├── tangkisan2kanan_rule_reference.json
    ├── tangkisan2kiri_rule_reference.json
    ├── tangkisan3kanan_rule_reference.json
    └── tangkisan3kiri_rule_reference.json
```

### 10 Kelas Global Model (`mlp_global_v8n.tflite`)

```
Index │ Label
──────┼────────────────
  0   │ Pukulan2_Kanan
  1   │ Pukulan2_Kiri
  2   │ Pukulan4_Kanan
  3   │ Pukulan4_Kiri
  4   │ Tangkisan1_Kanan
  5   │ Tangkisan1_Kiri
  6   │ Tangkisan2_Kanan
  7   │ Tangkisan2_Kiri
  8   │ Tangkisan3_Kanan
  9   │ Tangkisan3_Kiri
```

### Input/Output Model

| Model | Input | Output |
|-------|-------|--------|
| YOLOv8n-Pose (float16) | `[1, 640, 640, 3]` Float32 | `[1, 56, 8400]` Float32 |
| Global MLP (v8n) | `FloatArray(34)` (body-rel + scaled) | `FloatArray(10)` softmax |

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
    └─ [FAB Mulai Latihan] → ActivityKameraEvaluasi (langsung, tanpa dialog)
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
        └─── [Track 2] YOLO (tiap 500ms evaluasi / tiap 1000ms klasifikasi)
                 └─ Salin Bitmap dari frame
                 └─ Rotate sesuai orientasi
                 └─ YOLOv8n-Pose inferensi (background thread)
                 └─ Normalizer → MLP Global → Rule-based evaluasi
                 └─ Update UI + TTS (Main thread)
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
# Clone atau buka project di Android Studio:

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

*Dokumentasi ini diperbarui berdasarkan analisis source code SilatPRO — versi arsitektur global model (mlp_global_v8n.tflite).*
