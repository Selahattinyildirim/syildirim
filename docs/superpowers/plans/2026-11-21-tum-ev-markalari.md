# Tüm EV markaları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uygulamayı markadan bağımsız hale getirmek ve operatör bildirimleriyle otomatik kayıt önerisi eklemek.
**Architecture:** Saf mantık `core/` (VehicleCatalog, ChargeNotificationParser); Android tarafı `app/` (NotificationListenerService, onboarding, Bağlantılar).
**Tech Stack:** Kotlin, Compose M3, Room, DataStore, WorkManager, Robolectric.
**Spec:** docs/superpowers/specs/2026-11-21-tum-ev-markalari-design.md

## Global Constraints
- minSdk 26, targetSdk 35; yeni ağ izni yok; QUERY_ALL_PACKAGES kullanılmaz.
- Arayüz metinleri Türkçe; ad/ikon/metinde marka yok (Tesla yalnızca "Bağlantılar" altında).

## Review Focus
1. Alakasız bildirim (ör. "Kampanya: %20 indirim") şarj olayı sanılmamalı → parser UNKNOWN.
2. "1.234,56 TL" ve "₺412,50" ve "412.50 TL" doğru okunmalı.
3. Aynı tamamlanma bildirimi iki kez gelirse tek kayıt önerisi.
4. Bildirim erişimi verilmemişse ayar ekranı bunu açıkça göstermeli.
5. Kataloğda olmayan araç: elle ad ve kapasite.

### Task 1: VehicleCatalog (core)
- [ ] Test: `core/src/test/.../VehicleCatalogTest.kt` — arama ("togg" → T10X varyantları), kapasite, benzersiz id. Çalıştır → KIRMIZI.
- [ ] `core/.../VehicleCatalog.kt` → YEŞİL. Commit.

### Task 2: ChargeNotificationParser (core)
- [ ] Test: `ChargeNotificationParserTest.kt` — başladı/tamamlandı/ödeme TR+EN, kWh ve ₺ biçimleri, alakasız metin. KIRMIZI.
- [ ] Uygulama → YEŞİL. Commit.

### Task 3: Araç seçimi ve markasız metinler (app)
- [ ] Ayarlar: vehicleBrand/vehicleModel/onboarded; ilk açılış araç seçimi ekranı; Bluetooth marka eşleştirmesi; metinler.
- [ ] Testler: RemindersTest marka eşleştirmesi, AppFlow ilk açılış seçimi. Commit.

### Task 4: Operatör bildirim dinleyicisi (app)
- [ ] `ChargeNotificationListener` + `ChargeNotificationHandler` (tekrar engelleme, önceden doldurma), manifest servis + `<queries>`.
- [ ] Ayarlar "Bağlantılar": izin durumu, uygulama seçimi. Pro kapısı.
- [ ] Test: handler tamamlanma → bildirim + prefill; tekrar engelleme. Commit.

### Task 5: Doğrulama ve belgeler
- [ ] `gradle test assembleRelease bundleRelease`, ekran görüntüleri, gizlilik politikası ve PLAY_STORE güncellemesi, APK.
