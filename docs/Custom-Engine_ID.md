# Panduan Membuat Mesin Pencari Kustom — Bubble Android

[English](Custom-Engine.md) | **Bahasa Indonesia**

Bubble memungkinkan Anda menambahkan **Mesin Pencari Gambar (Visual)** dan **Mesin Pencari Teks Terpilih** sendiri langsung dari tab **Pencarian** tanpa perlu mengubah kode aplikasi.

---

## 1. Lokasi Pengaturan Mesin Kustom

1. Buka aplikasi **Bubble** → pilih tab **Pencarian** pada navigasi atas.
2. Gulir ke bagian:
   - **Mesin Pencari Gambar** → ketuk **+ Tambah Mesin Kustom** (untuk pencarian gambar terbalik / *reverse image search*), atau
   - **Mesin Pencari Teks Terpilih** → ketuk **+ Tambah Mesin Kustom** (untuk pencarian teks dari mode *Pilih Teks*).
3. Tahan dan geser ikon **`=`** di sebelah kiri tiap mesin (atau ketuk ikon **`=`** untuk memilih posisi angka) guna mengatur urutan, lalu ketuk **Default** untuk menjadikannya mesin utama.

---

## 2. Mesin Pencari Gambar (Visual) Kustom

Bubble mendukung **dua metode** untuk mesin pencari gambar kustom:

### Metode A — Mesin Pencari Web (`https://...`)

Kosongkan kolom **Android Package** dan masukkan alamat URL `http://` atau `https://` yang mendukung unggah gambar.

Saat Anda mencari gambar, Bubble memotong area seleksi, mengoptimalkannya sesuai preset **Kualitas Gambar** (*Asli / Seimbang / Hemat Data*), lalu membuka situs tersebut di WebView bawaan Bubble (atau browser eksternal jika opsi *Buka Mesin Web di Browser Eksternal* aktif) dan meneruskan gambar sementara ke input file situs bila didukung.

| Kolom | Syarat | Contoh |
| --- | --- | --- |
| **Nama Mesin** | 2–48 karakter | `IQDB` |
| **Base URL** | Harus diawali `https://` atau `http://` | `https://iqdb.org/` |
| **Android Package** | Biarkan **kosong** | *(kosong)* |

#### Contoh Mesin Pencari Gambar Web Populer

| Nama Mesin | Base URL | Cocok Untuk |
| --- | --- | --- |
| **IQDB** | `https://iqdb.org/` | Mencari sumber ilustrasi anime / manga |
| **ASCII2D** | `https://ascii2d.net/` | Ilustrasi Pixiv / Twitter Jepang |
| **Lenso.ai** | `https://lenso.ai/` | Pencarian gambar umum & objek |
| **PimEyes** | `https://pimeyes.com/` | Pencarian gambar web publik |
| **Karma Decay** | `http://karmadecay.com/` | Pencarian gambar dari Reddit |

> **Catatan Penting:** Bubble tidak pernah melakukan bypass CAPTCHA atau verifikasi anti-bot. Jika situs menampilkan verifikasi manusia atau meminta unggah manual, selesaikan langsung di halaman atau ketuk **Browser** di bilah atas.

---

### Metode B — Aplikasi Android Langsung (`com.example.app`)

Jika Anda memiliki aplikasi Android yang mendukung penerimaan gambar lewat `Intent.ACTION_SEND` (`image/*`), masukkan **Package Name** aplikasi tersebut pada kolom ketiga.

| Kolom | Syarat | Contoh |
| --- | --- | --- |
| **Nama Mesin** | 2–48 karakter | `Pinterest Lens` |
| **Base URL** | Opsional URL cadangan (`https://...`) | `https://www.pinterest.com/` |
| **Android Package** | Format ID paket Android yang valid (`a.b`) | `com.pinterest` |

#### Cara Mengetahui Package Name Aplikasi
- Buka tautan aplikasi di **Google Play Store** lewat browser — package name adalah nilai setelah `id=`:
  `https://play.google.com/store/apps/details?id=com.pinterest` → **`com.pinterest`**
- Atau cek menggunakan aplikasi Info Aplikasi / Package Viewer.

> **Fallback Otomatis:** Jika aplikasi tujuan belum terpasang atau tidak mendukung intent gambar langsung, Bubble otomatis mengalihkan ke dialog **Buka Dengan** bawaan Android agar pencarian tetap dapat berjalan.

---

## 3. Mesin Pencari Teks Terpilih (`{query}`)

Mesin pencari teks digunakan ketika Anda memilih teks biasa di mode **Pilih Teks** lalu mengetuk **Cari Web** (catatan: jika teks yang dipilih terdeteksi sebagai tautan/URL, tombol tersebut otomatis berubah menjadi **Buka Link**).

| Kolom | Syarat | Contoh |
| --- | --- | --- |
| **Nama Mesin** | 2–48 karakter | `Brave Search` |
| **Template URL Query** | URL `http://`/`https://` valid yang mengandung `{query}` | `https://search.brave.com/search?q={query}` |

Bubble akan otomatis melakukan *URL-encode* pada teks pilihan Anda dan mengganti `{query}` sebelum membukanya di browser.

#### Contoh Template Mesin Pencari Teks Siap Pakai

| Nama Mesin | Template URL Query |
| --- | --- |
| **Brave Search** | `https://search.brave.com/search?q={query}` |
| **Startpage** | `https://www.startpage.com/sp/search?query={query}` |
| **Kagi** | `https://kagi.com/search?q={query}` |
| **YouTube** | `https://www.youtube.com/results?search_query={query}` |
| **GitHub** | `https://github.com/search?q={query}` |
| **Wikipedia Indonesia** | `https://id.wikipedia.org/wiki/Special:Search?search={query}` |
| **Jisho (Kamus Jepang)** | `https://jisho.org/search/{query}` |
| **KBBI Daring** | `https://kbbi.kemdikbud.go.id/entri/{query}` |

---

## 4. Aturan Validasi & Pemecahan Masalah

| Pesan Error / Kendala | Penyebab & Solusi |
| --- | --- |
| *"Engine name must be 2–48 characters."* | Gunakan nama singkat antara 2 hingga 48 karakter. |
| *"Valid HTTP/HTTPS Base URL is required…"* | Pastikan URL diawali `https://` atau `http://` tanpa spasi. |
| *"Text search template must include `{query}`"* | Sertakan `{query}` (huruf kecil di dalam kurung kurawal) pada posisi kata kunci pencarian. |
| *"Invalid Android package name"* | Gunakan format paket dipisahkan titik seperti `com.vendor.app` tanpa spasi/simbol. |
| **Gambar ditolak situs karena terlalu besar** | Ubah **Preset Kualitas Gambar** ke **Seimbang** atau **Hemat Data** (atau turunkan *Resolusi Maksimal*) di tab **Pencarian**. |
| **Ingin mengembalikan daftar mesin bawaan?** | Gulir ke bagian bawah tab **Pencarian** lalu ketuk **Kembalikan Default**. |
