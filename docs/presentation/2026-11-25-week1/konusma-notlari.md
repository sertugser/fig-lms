# Konuşma notları: 1. hafta (25 Kasım 2026)

Slaytlar: [slides.md](slides.md). PDF için: `npx @marp-team/marp-cli slides.md --html --allow-local-files --pdf`

## Nasıl anlatacağız

- **Tek ana fikir:** "YZ ile yeniden yazmak kolay, aynı davrandığını kanıtlamak zor. Bu hafta o kanıtın altyapısını kurduk." Hoca başka hiçbir şey hatırlamasa bunu hatırlasın.
- **Okumak yok.** Slaytta yazanı tekrar etmeyin, slayt sizin söylediğinizin kanıtı. Notlarda her slayt için sadece ilk cümle yazılı (onu ezberleyin), gerisi anahtar kelime.
- **Örnekle başlayın, genel bilgiyle değil.** student3 hikâyesi tüm sunumun ipi: başta soruyoruz, demoda cevaplıyoruz.
- **Devir teslim:** kimse "Merhaba ben X, şimdi Y'yi anlatacağım" demesin. Bir önceki kişi bir cümleyle bağlayıp adını söylesin. Aşağıda her geçiş yazılı.
- **Konuşmayan da sunumda:** sıra sizde değilken telefona bakmayın, konuşana bakın.
- **Sonda "Teşekkürler" yok.** Son slayt "Bu hafta / Sıradaki / Sizin görüşünüz" ekranı, sorular boyunca o açık kalır. Kapanış cümlesi aşağıda.
- **Yedek slaytlar** (kapsam tablosu, tüm KPI'lar, mimari, YZ kaydı) sunumda gösterilmez; soru gelirse açılır.
- **Bir kez birlikte, süre tutarak prova edin**, geçişler dahil. Hedef 13 dakika, 2 dakika pay.

## Akış ve süre

| Slayt | Kim | Süre |
|---|---|---|
| 1–5 Açılış, problem, yöntem, Java | Sertug | 3,5 dk |
| 6 Şifre denemesi | Hasan | 1 dk |
| 7 CS101 / cs101 | Efekan | 1 dk |
| 8 Taslağa geri al | Sercan | 1,5 dk |
| 9 Kategori 404 | Oğuz | 1 dk |
| 10 KPI | Efekan | 1 dk |
| 11 Demo | Sertug | 2 dk |
| 12 5/5 | Oğuz | 1 dk |
| 13 Kapanış | Sertug | 1 dk |

---

### 1. Başlık (Sertug)
> "Hocam, projemizin zor kısmı kodu yazmak değil. Asıl zor kısım, yazdığımız şeyin Moodle ile aynı şekilde çalıştığını kanıtlamak. Bu hafta bunun için ne yaptığımızı anlatacağız."

Hemen ikinci slayta geç. Ekip tanıtımı yok, isimler slaytta.

### 2. student3 (Sertug)
> "Size örnek verilerimizden bir öğrenciyi göstereyim: student3."

- CS101'den 40 almış, geçme notu 50. Ama Moodle onu CS201'e kaydetmiş.
- Bu bir hata değil, Moodle'da böyle bir özellik hiç yok. 2012'den beri isteniyor, 99 oy almış, hâlâ yapılmamış.
- Soru olarak bırak: "Biz bunu nasıl düzelttik ve düzeltirken geri kalan her şeyi bozmadığımızı nasıl gösteriyoruz? Sunumun sonunda göstereceğiz."

### 3. YZ çevirisi (Sertug)
> "Yapay zekâ kodu başka bir dile çevirebiliyor. Ama çevirdiği kod gerçekten doğru çalışıyor mu? Literatüre baktık."

- **Sol kutu (küçük programlar):** "Bir çalışmada araştırmacılar yapay zekâya 1.700 küçük programı başka dillere çevirtmiş, sonra programların testlerini çalıştırmışlar. En iyi durumda bile 100 çeviriden sadece 47'si doğru çalışmış. En kötü durumda 100'de 2."
- **Sağ kutu (gerçek projeler):** "Bu küçük programlardı. Gerçek projelerde ne oluyor? Başka bir çalışmada 10 Java projesini parça parça Python'a çevirmişler. Parçaların yüzde 96'sında yazım hatası yok, yani kod ilk bakışta düzgün görünüyor. Ama doğru çalıştığını testle gösterebildikleri kısım sadece yüzde 25. Gerisini iki geliştirici proje başına yaklaşık 20 saatte elle düzeltmiş."
- **Bağla:** "Yani kodun düzgün görünmesi, doğru çalıştığı anlamına gelmiyor. Bu yüzden bizim için asıl soru şu: yeni sistemin Moodle ile aynı davrandığını nasıl kanıtlarız?"

### 4. Yöntem (Sertug)
> "O yüzden sırayı tersine çevirdik: önce Moodle'ı ölçüyoruz, sonra yazıyoruz."

- Dört adımı soldan sağa göster, her birine bir cümle.
- Bu hafta 41 kural, hepsi canlı Moodle'da denendi.
- YZ kaydı: her kullanımda model, bağlam, ne kadarını değiştirdiğimiz, süre. Proje dokümanı bunu istiyor.

### 5. Java (Sertug)
> "Dil seçerken tek bir soruyu sorduk: yapay zekânın yaptığı hatayı kim yakalayacak?"

- Java'da YZ ajanları TypeScript'in iki katı hata çözüyor.
- Derleyici ve modül sınırı testi, arkadaşımızın review'undan önce iki ayrı kontrol.
- **Geçiş:** "Kuralları çıkarırken Moodle'da beklemediğimiz şeyler de bulduk. İlki Hasan'dan."

### 6. Şifre (Hasan)
> "Moodle'da yanlış şifreyi istediğiniz kadar deneyebiliyorsunuz."

- Kilitleme eşiği sıfır, yani kilit kapalı. Hedefimiz 15 dakikada 5 hatalı denemede kilit.
- Oturum boşta 8 saat açık kalıyor. Hedef en fazla 2 saat.
- İyi haber de var: yetkiler doğru. Öğrenci URL'yi değiştirip başka derse giremedi.
- **Geçiş:** "Güvenlik tarafı böyle. Efekan ders kayıtlarında daha garip bir şey buldu."

### 7. CS101 / cs101 (Efekan)
> "Moodle'a göre CS101 ve küçük harfle cs101 iki farklı ders."

- 45 kuralın hepsi canlı Moodle'da doğrulandı.
- Tekillik veritabanında değil, sadece PHP kodunda. Bitişi başlangıçtan önce olan kayıt bile kabul ediliyor.
- Yeni sistemde bu kurallar veritabanında.
- **Geçiş:** "Bunlar tasarım zayıflıkları. Sercan gerçek bir Moodle hatasını yeniden üretti."

### 8. Taslağa geri al (Sercan)
> "Öğretmen bir teslimi taslağa geri aldığında, öğrenci fazladan bir deneme hakkı kazanıyor."

- Önce beklenen: öğrenci aynı denemeyi düzenleyip tekrar göndermeli.
- Ekran görüntüsünü göster: 2. deneme açılmış, dosya eski denemede kalmış.
- Neden: geri alma işlemi not güncellemesini tetikliyor, "otomatik yeniden aç" kuralı not verilip verilmediğine bakmıyor.
- **Geçiş:** "Oğuz da ikinci hatayı buldu, bu sefer öğrenci tarafında."

### 9. Kategori 404 (Oğuz)
> "Bir yönetici 'öğrenciler tüm dersleri gezmesin' dediğinde, öğrenciler kendi derslerini de göremiyor."

- Aynı ayarla öğrenci 404 alıyor, admin her şeyi görüyor.
- Tek komutla yeniden üretiliyor.
- Not hesaplamada 18 kural doğrulandı (ağırlıklı ortalama örneği sorulursa: 80 ve 40, ağırlık 1 ve 3 → 50).
- **Geçiş:** "Bunların hepsini düzelteceğiz. Ama 'düzelttik' dediğimizde neye göre? Efekan."

### 10. KPI (Efekan)
> "Yeni sistemi neyle kıyaslayacağımızı, tek satır kod yazmadan önce ölçtük."

- Üç sayı: Moodle'ın testlerinin hepsi geçiyor ama kodun sadece yüzde 31'ine dokunuyor; bizim hedefimiz 80. Giriş 116 ms, daha yavaş olmayacağız. Ödev oluşturma 8 tık, hedef 6.
- Toplam 26 ölçüt, dört kategori. Detay yedek slaytta.
- Dürüst ol: performans tek makinede ve küçük veriyle ölçüldü.
- **Geçiş:** "Şimdi baştaki soruya dönelim. Sertug."

### 11. Demo (Sertug)
> "student3'e geri dönelim. Aynı soruyu bizim sisteme soruyorum."

Sunumdan önce hazırla: Moodle (`docker compose -f infra/docker-compose.yml up -d`, `./infra/sample-data/load.sh`) ve backend (`cd backend && ./mvnw spring-boot:run`, `POSTGRES_*` ve `SAMPLE_USER_PASSWORD` ayarlı). Terminal yazısını büyüt.

- Komutu çalıştır: `allowed: false`, eksik CS101.
- Aynı komutu student1 ile çalıştır: `allowed: true`.
- Kural tek yerde, kayıt ve ders planı aynı yeri çağırıyor. 21 testle geliyor.
- Demo çalışmazsa slayttaki çıktıyı göster ve devam et. Özür dileyip uzatma.
- **Geçiş:** "Peki bu cevabın doğru olduğunu nereden biliyoruz? Oğuz."

### 12. 5/5 (Oğuz)
> "Aynı beş durumu hem Moodle'a hem bizim sisteme sorduk."

- Önce farkı netleştir: Moodle "CS101'i tamamladı mı?" bilgisini biliyor ama kayıtta buna hiç bakmıyor, o yüzden student3'ü alıyor. Biz kapıda bakıyoruz. Golden master, kapıda baktığımız bilginin Moodle'ınkiyle aynı olduğunu kanıtlıyor: Moodle "tamamlamadı" diyorsa biz "kaydolamaz", "tamamladı" diyorsa "kaydolabilir" diyoruz.

- Beşinde de cevap Moodle'ın kendi tamamlama verisiyle tutarlı. Test main'de, her PR'da CI'da çalışıyor.
- Güven veren kısım: sistemi bilerek bozduk, test farkı satır satır yakaladı.
- Dürüst not: "başkasını soruyor" durumunda Moodle tarafı canlı çağrı değil, kaynak koddaki kural, çünkü test token'ı admin'in.
- **Geçiş:** "Sertug toparlıyor."

### 13. Kapanış (Sertug)
> "Kısacası: kanıtlama altyapısı hazır, şimdi modülleri tek tek yazmaya geçiyoruz."

- Sol kutu bir cümle, sağ kutu bir cümle. Okuma.
- Hocaya soru: "YZ destekli ve manuel geliştirmeyi karşılaştırmak için hangi alt fonksiyonu seçmemizi önerirsiniz?" Sonra sus ve bekle.
- Eksikleri biz söyleyelim: eğitim planı gereksinimleri, arayüz, bir kullanılabilirlik ölçümü ve ZAP taraması henüz yok.
- Bitiş: "Sorularınızı alabiliriz." Slayt açık kalır.

## Olası sorular

- **Bu çalışmalardaki "testler" ne?** Eski kodun kendi testleri; çeviriyi aynı testlerle sınamışlar. Test olmayan yer kanıtlanamıyor (AlphaTrans'ta %25'in nedeni). Moodle'ın testleri geçiyor ama kodun sadece ~%31'ine dokunuyor; bu yüzden onlarla yetinmiyoruz, aynı girdiyi çalışan Moodle'a ve yeni sisteme verip karşılaştırıyoruz.
- **Neden Java?** Derleyici + modül sınırı testi; yedek slayt "Mimari kararı".
- **YZ'nin yazdığı koda nasıl güveniyorsunuz?** Derleyici, modül testi, arkadaş review'u, sonra çalışan Moodle'a karşı golden master.
- **Performans sayıları neden bu kadar küçük?** Küçük veri, tek makine; sadece aynı koşulda yeni sistemle kıyaslamak için.
- **YZ kabul oranınız ne?** Kayıtların bir kısmı henüz "pending", haftalık raporda vereceğiz.
- **Danışman onayı, kredi sınırı, ders planı?** Moodle'da karşılığı yok; yeni gereksinim olarak sırada. Ders planı ön koşul kontrolünü kullanacak.
