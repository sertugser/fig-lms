# Konuşma notları: 1. hafta sunumu (25 Kasım 2026)

Slaytlar: [slides.md](slides.md) (Marp). PDF almak için: `npx @marp-team/marp-cli slides.md --allow-local-files --pdf`.

Toplam hedef süre: yaklaşık 15 dakika + sorular. Her modül sahibi kendi slaytını anlatır (slaytın sağ üstünde isim var).

| # | Slayt | Konuşan | Süre |
|---|---|---|---|
| 1–7 | Giriş, kapsam, mimari, literatür, yöntem, SRS | Sertug | 4 dk |
| 8 | Kimlik ve yetki | Hasan | 1 dk |
| 9 | Ders ve kayıt | Efekan | 1 dk |
| 10 | Danışmanlık ve MDL-22648 | Sertug | 1 dk |
| 11–12 | Ödev ve MDL-87490 | Sercan | 1,5 dk |
| 13 | Değerlendirme ve MDL-85503 | Oğuz | 1 dk |
| 14 | KPI baseline | Sertug (her satırı sahibi bir cümleyle) | 1,5 dk |
| 15 | İlk kod + canlı demo | Sertug | 2 dk |
| 16 | Golden master | Oğuz | 1 dk |
| 17–19 | YZ kaydı, eksikler, kapanış | Sertug | 1 dk |

## 1. Başlık
"Projemiz Moodle'ın seçilmiş alt fonksiyonlarını yapay zekâ desteğiyle yeni bir dil ve mimaride yeniden yazmak ve bunu ölçülebilir kanıtlarla yapmak. Bugün ilk haftanın çıktılarını gösteriyoruz."

## 2. Bu hafta ne yaptık?
- Proje dokümanındaki iş paketlerine bağla: bu hafta WBS-1 bitti, WBS-2 başladı.
- Vurgu: "Her kuralı sadece kaynak koddan okumadık, çalışan Moodle'da denedik." ve "KPI'ları geliştirmeye başlamadan ölçtük."
- Frontend sorulursa: bu hafta bilerek analiz ve ölçüme odaklandık.

## 3. Kapsam
- Sürümü sabitledik: 5.3.1, tek commit. Tüm analiz ve ölçümler aynı sürümde.
- Proje dokümanındaki modül listesinden geliyor; dosya yönetimi ödev modülündeki dosya teslimi olarak kapsamda.
- 165 bin satır PHP: bu yüzden kapsamı akışlarla sınırladık.

## 4. Mimari kararı
- Üç gerekçe: YZ ajanları Java'da daha başarılı, tip sistemi YZ hatalarını derlemede yakalıyor, modül sınırları CI'da otomatik kontrol ediliyor.
- Kendi sınırlılığımızı söyle: benchmark hata düzeltmeyi ölçüyor; iddiamız ölçülü.
- Detay: `docs/adr/0001-language-and-architecture.md`.

## 5. Literatür ve özgünlük
- Mesaj: "YZ ile çeviri tek başına güvenilir değil, literatür de bunu söylüyor. Bizim farkımız davranışı çalışan eski sisteme karşı kanıtlamak."
- Kaynak: `docs/methodology/literature.md`.

## 6. Yöntem
- Bir kuralın yolculuğunu anlat: Moodle'da bul → REQ ID ver → YZ ile yaz → arkadaşın review etsin → golden master ile Moodle'a karşı test et → KPI ile ölç.
- "Uzman kontrol noktası" proje dokümanındaki ifade: bizde her PR'ın bir ekip arkadaşı onayı.

## 7. SRS
- 41 modül gereksinimi + 5 kalite gereksinimi. ISO/IEC/IEEE 29148 yapısı.
- Eğitim planı gereksinimleri boş, açıkça söyle (son slaytta da var).

## 8. Kimlik ve yetki (Hasan)
- Moodle'ın rolleri bizim üç rolümüze nasıl eşlendi.
- Bulgu: hesap kilitleme kapalı, oturum 8 saat; bunlar yeni sistemde güvenlik hedefi.

## 9. Ders ve kayıt (Efekan)
- 45 kontrol canlı Moodle'da geçti.
- İlginç bulgular: `cs101` / `CS101` kabul ediliyor; bitişi başlangıçtan önce olan kayıt kaydediliyor ama hiç aktif olmuyor; tekillik veritabanında değil.

## 10. Danışmanlık ve MDL-22648 (Sertug)
- "Moodle'da danışmanlık yok; ön koşul özelliği 2012'den beri bekliyor, 99 oy."
- Neden yapılamadığı: kayıt eklentilere dağılmış. Bizde kural tek modülde, herkes API'den çağırıyor.

## 11–12. Ödev ve MDL-87490 (Sercan)
- Hatayı adım adım anlat, sonra ekran görüntüsünü göster: geri almadan sonra 2. deneme açılmış.
- Kök neden tek cümle: "otomatik yeniden açma" not verilip verilmediğine bakmıyor.
- Canlı demo istenirse adımlar `docs/bugs/MDL-87490.md` "Live demo" bölümünde (1–2 dk).

## 13. Değerlendirme ve MDL-85503 (Oğuz)
- Ağırlıklı ortalama örneği: 80 ve 40, ağırlık 1 ve 3 → 50.
- MDL-85503 bir komutla yeniden üretiliyor; hatanın aslında kategori/ders modülüne ait olduğunu söyle.

## 14. KPI'lar
- 26 KPI, dört kategori, hepsinin Moodle değeri var. Hedefler yazılı.
- Sınırlılıkları kendimiz söyleyelim: performans tek makine ve küçük veri; ZAP yapılmadı; FLOW-05 insan ölçümü eksik.

## 15. İlk kod + demo (Sertug)
Demo öncesi hazırlık (sunumdan önce):
```bash
docker compose -f infra/docker-compose.yml up -d      # Moodle
./infra/sample-data/load.sh
cd backend && ./mvnw spring-boot:run                    # POSTGRES_* ve SAMPLE_USER_PASSWORD ayarlı olmalı
```
Demo akışı:
1. Moodle'da CS201 katılımcı listesini aç: student3 kayıtlı (CS101'den 40 aldı, geçme notu 50). Yedek: örnek veri bunu bilerek kuruyor (`infra/sample-data/load.php`, "student3 is enrolled in CS201 although CS101 is not completed").
2. Terminalde slayttaki `curl` komutunu çalıştır: `allowed:false`, eksik `CS101`.
3. Karşılaştırma için `student1` ile aynı komut: `allowed:true`.
- Endpoint 21 testle geliyor. Demo çalışmazsa slayttaki JSON çıktısı yedek.

## 16. Golden master (Oğuz)
- Aynı girdi iki sisteme gidiyor, 5/5 aynı sonuç. Sistem bilerek bozulunca test farkı satır satır gösterdi.
- Dürüst not: "Yetkisiz" senaryosunda Moodle tarafı canlı çağrı değil, kaynak koddaki kural; test token'ı admin'e ait.
- Moodle tarafı Moodle çalışmıyorsa snapshot'lardan okunuyor; rapor hangisinin kullanıldığını yazıyor.
- Durum: PR #43 ile main'e merge edildi; CI her PR'da çalıştırıp tabloyu job özetinde gösteriyor.
- Tek komut: `tests/golden-master/run-prerequisite-golden-master.sh`.

## 17. YZ kullanım kaydı
- Proje dokümanı YZ katkısının kaydedilmesini istiyor: model, bağlam, düzeltmeler, emek. Hepsi kayıtta.
- Kabul oranını henüz vermiyoruz çünkü bazı kayıtlar "pending"; bunu açıkça söyle.

## 18. Eksikler ve sonraki adımlar
- Eksikleri biz söyleyelim, hoca sormadan.
- Sonraki hafta: modül modül geliştirme, her korunan kural için golden master, iki hata için önce başarısız test.

## Olası sorular
- **Neden Java?** Slayt 4; ADR-0001'deki kanıtlar.
- **YZ'nin yazdığı koda nasıl güveniyorsunuz?** Derleyici + modül sınırı testi + arkadaş review'u + golden master (çalışan Moodle'a karşı).
- **Performans sayıları neden bu kadar küçük?** Küçük örnek veri, sıcak önbellek, tek makine; yalnızca aynı koşulda yeni sistemle karşılaştırmak için.
- **Danışman onayı, kredi sınırı?** Moodle'da karşılığı yok, yeni gereksinim olarak sonra yazılacak.
- **Eğitim planı modülü?** Ön koşul kontrolünü kullanacak; gereksinimleri henüz yazılmadı.
