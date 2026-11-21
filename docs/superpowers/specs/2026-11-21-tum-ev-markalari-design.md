# Tüm elektrikli araçlar için Şarj Defteri – Tasarım

**Durum:** Onaylandı (yaklaşım A) · 21 Kasım 2026

## Amaç
Uygulama Tesla'ya değil, Türkiye'deki tüm EV sahiplerine (Togg, BYD, Renault, Hyundai, Tesla…) hitap etsin. Ticari büyüme için
kitle genişliyor. Tesla dışındaki markaların açık araç API'si yok; otomasyon markadan bağımsız kaynaklardan gelmeli.

## Yaklaşımlar
| | Yaklaşım | Karar |
|---|---|---|
| A | Şarj operatörü uygulama bildirimleri + Bluetooth + manuel; Tesla Fleet API isteğe bağlı eklenti | **Seçildi.** Her markada çalışır, sunucu yok |
| B | Yalnızca manuel + Bluetooth; Tesla eklenti | Tesla dışında otomasyon yok |
| C | OBD-II Bluetooth adaptörü | Ek donanım, düşük uyumluluk |

## Tasarım
1. **Araç kataloğu (core):** Marka/model/varyant ve yaklaşık kullanılabilir batarya kapasitesi. İlk açılışta araç seçimi;
   kapasite otomatik dolar, düzenlenebilir. Listede olmayan araç için "Diğer" (ad + kapasite elle).
2. **Markasız metinler:** "Araca mı bindin?" vb. Bluetooth'ta cihaz seçilmemişse seçili aracın marka adı cihaz adında aranır
   (ör. "TOGG", "BYD"); Tesla BLE deseni yalnızca marka Tesla iken.
3. **Operatör bildirimleri (NotificationListenerService):**
   - Kullanıcı izleyeceği uygulamaları seçer (yüklü uygulamalardan; ZES, Trugo, Eşarj, Voltrun, Astor, Sharz, Tesla… adları
     otomatik önerilir). Seçilmeyen uygulamaların bildirimleri okunmaz.
   - `core.ChargeNotificationParser` başlık+metinden olay türünü (başladı / tamamlandı / ödeme), kWh'i ve ₺ tutarı çıkarır.
     Türkçe ve İngilizce anahtar kelimeler, Türkçe sayı biçimleri (1.234,56).
   - Başladı → hafif bildirim "X'te şarj başladı", başlangıç zamanı saklanır. Tamamlandı/ödeme → "Şarj Defteri'ne kaydet:
     42,3 kWh · 412,50 ₺"; dokununca form dolu açılır (istasyon = operatör adı, tutar, kWh, başlangıç zamanı) ve kampanya önerisi çalışır.
   - Tekrar engelleme: aynı uygulama + olay için 30 dk içinde tek bildirim; tamamlanma, aynı oturumda tek sefer.
4. **Bağlantılar bölümü (Ayarlar):** Operatör bildirimleri · Araç Bluetooth'u · Tesla (Fleet API).
5. **Pro = Otomasyon paketi:** operatör bildirimleri + Tesla + gelişmiş analiz + Excel. Kampanyalar, Bluetooth ve manuel kayıt ücretsiz.

## Kısıtlar
- Bildirim erişimi kullanıcı izni ister; Play dışı kurulumda Android 13+ "kısıtlanmış ayar" adımı gerekir.
- Operatör bildirim metinleri bilinmiyor/değişebilir: ayrıştırılamayan sayılar boş bırakılır, olay tanınırsa yine bildirim gelir.
- Katalog kapasiteleri yaklaşıktır; kullanıcı düzenleyebilir.
- Paket görünürlüğü için yalnızca LAUNCHER uygulamaları sorgulanır (QUERY_ALL_PACKAGES yok).

## Test
- core: katalog araması, bildirim ayrıştırıcı (TR/EN, sayı biçimleri, olay türleri, alakasız bildirim).
- app: bildirim olayından önceden doldurulmuş kayıt akışı, tekrar engelleme, Bluetooth marka eşleştirmesi, ilk açılış araç seçimi (Robolectric).
