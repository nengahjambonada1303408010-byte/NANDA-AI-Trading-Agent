# NANDA AI Trading Agent

Aplikasi Android overlay service dengan fitur AI Agent untuk trading otomatis. Menampilkan floating ball saat membuka aplikasi trading (MT5, TradingView, dll) yang bisa diklik untuk analisa AI dan eksekusi trading otomatis via API Bybit.

## Fitur Utama

- **Floating Ball Overlay** - Muncul otomatis saat membuka MT5/TradingView, bisa dipindahkan dan diklik
- **AI Analysis** - Analisa market real-time menggunakan Moonshot AI (Kimi) API
- **Auto Trading** - Eksekusi order otomatis berdasarkan sinyal AI
- **Risk Management** - Setting risk per trade (0.5% - 5%), leverage, TP/SL
- **Market Monitor** - Monitoring harga real-time dari Bybit
- **Trade History** - Riwayat trading tersimpan lokal
- **Deteksi Otomatis** - Accessibility service mendeteksi saat aplikasi trading dibuka

## Arsitektur

```
app/src/main/java/com/nanda/ai/trading/
├── NandaApplication.kt           # Application class & dependency injection
├── overlay/
│   ├── FloatingBallService.kt    # Overlay service (floating ball)
│   ├── FloatingBallView.kt       # Custom view floating ball
│   ├── ControlPanelView.kt       # Panel kontrol analisa & trading
│   ├── TradingAppDetector.kt     # Accessibility service detector
│   └── BootReceiver.kt           # Auto-start saat boot
├── ai/
│   ├── AIAnalyzer.kt             # Interface AI analysis
│   ├── MoonshotAI.kt             # Implementasi Moonshot/Kimi API
│   └── AnalysisResult.kt         # Data class hasil analisa
├── trading/
│   ├── TradingEngine.kt          # Engine eksekusi trading
│   ├── BybitAPI.kt               # Bybit REST API client
│   ├── OrderManager.kt           # Manajemen order
│   └── TradeResult.kt            # Data class hasil trade
├── data/
│   ├── MarketData.kt             # Data class market data
│   ├── TradingConfig.kt          # Konfigurasi
│   └── AppDatabase.kt            # Room database
├── ui/
│   ├── MainActivity.kt           # Activity utama (settings)
│   ├── SetupActivity.kt          # Setup awal
│   └── TradeHistoryActivity.kt   # Riwayat trading
└── utils/
    ├── Constants.kt              # Konstanta
    ├── PermissionHelper.kt       # Helper permission
    └── NetworkUtils.kt           # Utils jaringan
```

## Persyaratan

- Android 8.0 (API 26) atau lebih tinggi
- Koneksi internet
- Akun Bybit (main/testnet)
- API Key Moonshot AI (opsional, untuk analisa AI)

## Cara Build

### Opsi 1: Android Studio (Direkomendasikan)

1. Clone/copy project ini
2. Buka di Android Studio Hedgehog atau lebih baru
3. Sync Gradle
4. Build → Build Bundle(s) / APK(s) → Build APK
5. Install APK ke device

### Opsi 2: Command Line (dengan Gradle Wrapper)

```bash
cd ai-trading-agent

# Linux/Mac
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug

# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

### Opsi 3: Release Build

```bash
# Generate keystore (satu kali)
keytool -genkey -v -keystore nanda-release.keystore -alias nanda -keyalg RSA -keysize 2048 -validity 10000

# Build release
./gradlew assembleRelease -Pandroid.injected.signing.store.file=nanda-release.keystore -Pandroid.injected.signing.store.password=YOUR_PASSWORD -Pandroid.injected.signing.key.alias=nanda -Pandroid.injected.signing.key.password=YOUR_PASSWORD
```

## Cara Install

1. Copy file APK ke Android device
2. Aktifkan "Install from unknown sources" di Settings > Security
3. Install APK
4. Ikuti setup wizard pertama kali

## Cara Penggunaan

### Setup Pertama Kali

1. **Buka aplikasi NANDA AI Trading**
2. **Berikan izin Overlay** - Agar floating ball bisa muncul di atas aplikasi lain
3. **Berikan izin Accessibility** - Agar NANDA bisa mendeteksi saat MT5/TradingView dibuka
4. **Masukkan API Key Bybit** - Dari menu API Management di akun Bybit Anda
5. **Masukkan API Key Moonshot** (opsional) - Untuk analisa AI (daftar di platform.moonshot.cn)

### Penggunaan Sehari-hari

1. **Buka MT5 atau TradingView** seperti biasa
2. **Floating ball NANDA akan muncul otomatis** di pojok kanan layar
3. **Klik floating ball** untuk membuka panel kontrol
4. **Tap "ANALISA AI"** untuk mendapatkan analisa real-time
5. **Tap "BUY" atau "SELL"** untuk eksekusi manual
6. Atau aktifkan **Auto Trading** di settings untuk eksekusi otomatis

### Pengaturan

| Setting | Default | Range | Keterangan |
|---------|---------|-------|------------|
| Symbol | XAUUSD | Bebas | Pair trading yang dipantau |
| Timeframe | 15 menit | 1m - 1D | Timeframe analisa |
| Risk/Trade | 1% | 0.5% - 5% | Persentase risk per trade |
| Leverage | 20x | 1x - 100x | Leverage trading |
| Auto Trade | OFF | ON/OFF | Eksekusi otomatis |
| Testnet | ON | ON/OFF | Gunakan testnet Bybit |

## API Keys

### Bybit API

1. Login ke [Bybit](https://www.bybit.com)
2. Account & Security → API Management
3. Create New Key
4. Enable: **Read-Write**, **Contract**
5. Copy API Key dan Secret
6. Paste ke menu API Keys di aplikasi NANDA

### Moonshot AI API

1. Daftar di [Moonshot Platform](https://platform.moonshot.cn)
2. Buat API Key
3. Copy API Key
4. Paste ke menu API Keys di aplikasi NANDA

> **Note**: Moonshot API bersifat opsional. Jika tidak diatur, NANDA akan menggunakan analisa teknikal sederhana (fallback).

## Keamanan

- API Key disimpan di **SharedPreferences encrypted** (private mode)
- Tidak ada data yang dikirim ke server pihak ketiga
- Koneksi ke Bybit menggunakan HTTPS
- Moonshot API hanya mengirim data candlestick, tidak ada informasi akun

## Troubleshooting

| Problem | Solusi |
|---------|--------|
| Floating ball tidak muncul | Cek izin Overlay di Settings > Apps > NANDA AI > Overlay Permission |
| Tidak terdeteksi saat buka MT5 | Cek izin Accessibility di Settings > Accessibility > NANDA AI Trading Detector |
| API Error | Cek API Key dan Secret Bybit, pastikan tidak expired |
| AI tidak menjawab | Cek API Key Moonshot, pastikan masih aktif dan ada quota |
| Trade gagal | Cek saldo akun, leverage, dan minimal order size |

## Catatan Penting

- **Gunakan Testnet dulu** untuk testing sebelum trading dengan uang sungguhan
- **Trading forex/CFD memiliki risiko tinggi**, gunakan dengan bijak
- Aplikasi ini adalah alat bantu, bukan jaminan profit
- Pastikan device selalu terkoneksi internet saat overlay aktif

## Lisensi

Proyek ini untuk penggunaan pribadi. Trading adalah aktivitas berisiko, gunakan dengan tanggung jawab.

---

**NANDA AI Trading Agent v1.0**
