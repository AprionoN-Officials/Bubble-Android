# Bubble Translation Studio (`tools/i18n/`)

Paket lokal lengkap untuk mempermudah kontribusi terjemahan **Bubble Android** menggunakan **Excel (`.xlsx`)** + **Web Previewer 1:1** tanpa perlu menyentuh XML atau Android Studio.

## Isi Folder
- `export_template.py` — Mengekstrak seluruh string dari `app/src/main/res/values/strings.xml` (EN) & `values-in/strings.xml` (ID) menjadi template Excel multi-sheet berformula + `previewer/default-data.js`.
- `import_excel.py` — Memvalidasi placeholder (`%1$s`, `%1$d`, dll.) dan panjang tombol, lalu mengonversi `.xlsx` / `.csv` menjadi file `strings.xml` Android siap pakai.
- `templates/` — Template Excel siap isi (`Bubble-v1.1.1-Translation-Template.xlsx`) dan contoh terisi Bahasa Indonesia (`Bubble-v1.1.1-Sample-ID.xlsx`).
- `previewer/index.html` — Aplikasi Web **1:1 UI Simulator** (bisa dibuka langsung di browser tanpa server!) untuk menguji file `.xlsx` secara real-time di mockup 7 Tab + Overlay + Dialog Safe Mode + Light/Dark/AMOLED + layar `360dp`/`412dp` + mode RTL + ekspor `strings.xml` 1 klik.

## Cara Pakai Cepat

### 1. Generate Ulang Template Excel dari `strings.xml`
```powershell
python tools/i18n/export_template.py --with-sample-id
```

### 2. Preview 1:1 di Browser (Drag & Drop `.xlsx`)
Buka langsung `tools/i18n/previewer/index.html` di Chrome / Edge / Firefox, lalu tarik file `.xlsx` Anda ke kotak unggah (atau klik tombol **Muat Bahasa: EN / ID** untuk mencoba langsung).
- Klik teks apa pun di mockup HP untuk langsung mengeditnya secara real-time!
- Gunakan tombol **360dp (Compact)** untuk mendeteksi teks yang terpotong atau meluber.
- Klik **Export strings.xml** langsung dari browser atau gunakan CLI di langkah 3.

### 3. Validasi & Konversi Excel → Android `strings.xml` via CLI
```powershell
python tools/i18n/import_excel.py tools/i18n/templates/Bubble-v1.1.1-Sample-ID.xlsx --out tools/i18n/out/strings-id.xml
```
