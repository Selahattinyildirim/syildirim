# Şarj Defteri – Google Play'e Çıkış ve Gelir Planı

## 1. Kimlik (kalıcı – değiştirmeyin)
| | |
|---|---|
| Uygulama adı | Şarj Defteri |
| Paket adı | `com.sarjdefteri.app` |
| Yükleme anahtarı | `signing/sarjdefteri-upload.jks` (git dışı), alias `sarjdefteri-upload` |
| Sertifika | `CN=Sarj Defteri, OU=Mobile, O=Sarj Defteri, L=Istanbul, C=TR`, RSA 4096, 2056'ya kadar geçerli |
| Pro ürün kimliği | `sarj_defteri_pro` (uygulama içi ürün, tüketilemez) |

Sertifikada kişi adı değil **marka adı** var: uygulama ileride bir şirkete devredilirse veya ortak eklenirse kimlik değişmez,
kişisel bilgi APK'dan okunamaz. Play App Signing açılınca asıl imza anahtarını Google saklar; bu dosya yalnızca **yükleme**
anahtarıdır. Kaybolursa Play Console'dan sıfırlama istenebilir, ama yine de iki ayrı yerde (ör. parola yöneticisi + şifreli bulut) saklayın.

## 2. Pazar araştırması (Kasım 2026)
**Genel EV şarj kayıt uygulamaları (Play'de çok sayıda):** EV Charging Cost Tracker, EV Charge Monitor, Plugged EV, ChargeTrack,
WattMate, PlugLog. Hepsi kWh × birim fiyat, ₺/km ve aylık grafik sunuyor, yani bu temel özellikler tek başına ayırt edici değil.

**Türkiye'de:** Şarj Yönetimi (com.sarj.yonetimi, sarjyonetimi.com), EVAsist (harita + topluluk + masraf), Silent But Fast (otomatik sürüş
kaydı), RangeHop (fiş fotoğrafından kayıt), Şarjsepeti, ChargeIQ/Voltla/Kilovati (istasyon bulma ve fiyat karşılaştırma). Operatör uygulamaları
(ZES, Eşarj, Trugo…) yalnızca kendi şarjlarını gösterir.

**Tesla odaklı ücretli uygulamalar:** Tessie, Teslascope, TeslaFi, Stats for Tesla. Aylık yaklaşık 4–8 $ abonelik veya ömür boyu
lisans satıyorlar. Bu fiyatlar, Tesla sahiplerinin otomasyona para ödediğini gösteriyor.

**Şarj Defteri'nin farkı:**
1. **Banka kartı kampanyaları:** Hangi kartla hangi istasyonda ne kadar indirim alındığını hesaplıyor ve şarj anında en avantajlı kartı öneriyor.
   Rakiplerde bu özelliği bulamadım ve Türkiye'ye özgü bir ihtiyaç.
2. Türkçe ve Türk bankalarına göre ayarlanmış bir arayüz, ₺ ile Türk tipi CSV/Excel çıktısı.
3. Tek seferlik Pro: abonelik yorgunluğu olan kullanıcıya cazip.
4. Bluetooth ile hatırlatma: sunucu ve konum izni gerektirmeden çalışıyor.

## 3. Gelir modeli
- **Ücretsiz:** Sınırsız kayıt, panel, temel grafikler, CSV, kampanyalar, Bluetooth hatırlatmaları. Amaç kullanıcı kazanmak ve iyi puan almak.
- **Pro (tek seferlik, öneri 149–199 ₺):** Tesla otomasyonu, gelişmiş analizler, Excel.
- **İleride – kampanya akışı:** Bizim hazırladığımız, kaynağı belirtilmiş, aylık güncel kampanya listesi (uygulama açılınca uzaktan JSON olarak çekilir).
  Abonelik için en güçlü aday bu; kullanıcı her ay geri gelir.
- **İleride – Tesla otomasyonunun kitleye açılması:** Şu an her kullanıcı kendi Tesla geliştirici kimliğini giriyor. Bu teknik kullanıcıya uygun,
  kitleye değil. Kitlesel kullanım için uygulamanın kendi Tesla uygulama kaydı ve küçük bir token sunucusu gerekir (client secret uygulamaya gömülemez).
  Tesla Fleet API kullanım başına ücretlidir (araç verisi isteği başına yaklaşık 0,002 $, hesap başına aylık yaklaşık 10 $ ücretsiz kredi; güncel tabloyu
  developer.tesla.com'da kontrol edin). Bu yüzden bu özellik **abonelik** olmalı (ör. 49 ₺/ay).
- Reklam önerilmez: küçük kitlede gelir düşük, deneyim ve gizlilik iddiası zarar görür.

Gerçekçi beklenti: Türkiye'de EV sayısı hızla artıyor, ama niş bir uygulama ilk yıl genellikle ayda birkaç yüz ile birkaç bin TL arası gelir getirir.
Asıl fırsat Togg ve Tesla topluluklarında (forumlar, Facebook/WhatsApp grupları) organik büyüme ve kampanya özelliğinin her ay geri dönüş sağlaması.

## 4. Kampanyalar – hukuki çerçeve (avukat görüşü değildir)
- Kampanyanın varlığı ve oranı gibi **olgular** telif konusu değildir. Ama bir WhatsApp kanalının **metinlerini, görsellerini veya derlemesini**
  kopyalamak, otomatik olarak toplamak (scraping) veya yeniden yayınlamak telif ve WhatsApp kullanım koşulları açısından risklidir. Bu yüzden uygulama
  kanalı yalnızca **bağlantı olarak açar**, içeriğini çekmez.
- Kullanıcının kendi girdiği kampanyalar cihazında kalır; sorun yok.
- Kampanya akışını biz yayınlarsak: bilgiyi bankanın **resmi kampanya sayfasından** kendi cümlelerimizle yazıp kaynak bağlantısı vermeliyiz,
  "bilgi amaçlıdır, bağlayıcı değildir, banka ile ilişkimiz yoktur" uyarısı koymalıyız, banka **logolarını izinsiz kullanmamalıyız** (adı geçirmek
  tanımlayıcı kullanım olarak genelde kabul edilir) ve ücretli/sponsorlu kampanya gösterirsek bunu "reklam" olarak işaretlemeliyiz.
- Uygulama adında ve ikonunda "Tesla" markası yok. Mağaza açıklamasında "Tesla ile uyumlu" demek tanımlayıcı kullanımdır; logo kullanılmaz.

## 5. Play Console kontrol listesi
1. Geliştirici hesabı (tek seferlik 25 $). Kişisel hesaplarda **üretime çıkmadan önce en az 12 test kullanıcısıyla 14 gün kapalı test** şartı var (Google'ın yeni kişisel hesaplar için koyduğu kural; güncel durumunu Console'da kontrol edin).
2. Uygulama oluştur → paket: `com.sarjdefteri.app` → Play App Signing'i kabul et.
3. `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` dosyasını kapalı test kanalına yükle.
4. Para kazanma → Uygulama içi ürünler → `sarj_defteri_pro` oluştur, fiyatı belirle (ödeme profili ve vergi bilgisi gerekir).
5. Gizlilik politikası URL'si: `docs/PRIVACY_POLICY.md`'yi GitHub Pages veya basit bir web sayfasında yayınla.
6. Veri güvenliği formu: "Veri toplanmıyor / paylaşılmıyor". Satın alma Google Play'de işlenir; Tesla verisi cihazda kalır.
7. İçerik derecelendirmesi anketi, hedef kitle (18+ veya 13+), reklam yok.
8. Mağaza görselleri: `docs/demo`, `docs/screenshots` (en az 2 telefon ekran görüntüsü, 512×512 ikon, 1024×500 tanıtım görseli).

## 6. Mağaza metni (taslak)
**Kısa açıklama (80):** Elektrikli araç şarj maliyetini, kWh/100 km tüketimini ve kart kampanyalarını takip et.

**Uzun açıklama:**
Şarj Defteri, elektrikli aracının her şarjını 20 saniyede kaydetmeni ve gerçek maliyetini görmeni sağlar.
• Tutar, kWh, birim fiyat, indirim, banka, kilometre ve batarya bilgisi
• Önceki şarjdan bu yana yapılan km, kWh/100 km ve km başına maliyet
• Aylık harcama ve enerji grafikleri, istasyon karşılaştırması
• Banka kartı ve operatör kampanyaları: şarj anında en avantajlı kartı gösterir
• Araca binince/inince Bluetooth hatırlatmaları
• CSV ve Excel dışa aktarma
• Pro: Tesla ile otomatik km/batarya/şarj bitişi (Tesla Fleet API)
Verilerin yalnızca telefonunda kalır. Hesap yok, reklam yok.
Tesla, ilgili şirketin ticari markasıdır; Şarj Defteri bağımsız bir uygulamadır.
