# Konuşma notları: 1. hafta (25 Kasım 2026)

Slaytlar: [slides.md](slides.md). PDF için: `npx @marp-team/marp-cli slides.md --html --allow-local-files --pdf`

## Nasıl anlatacağız

- **Tek ana fikir:** "YZ ile yeniden yazmak kolay, aynı davrandığını kanıtlamak zor. Bu hafta o kanıtın altyapısını kurduk." Hoca başka hiçbir şey hatırlamasa bunu hatırlasın.
- **Slayt kendini anlatıyor.** Her slaytın başlığı o slaytın ana fikri, altındaki cümleler de açıklaması. Takılırsanız slayttaki cümleyi okuyun. Notlarda her slayt için söylenecek ilk cümle ve birkaç kısa madde var.
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
> "Peki biz nasıl çalışıyoruz? Her kuralı önce Moodle'da deniyoruz, sonra yeni sistemde aynı sonucu arıyoruz."

- Önce ortamı söyle: Moodle 5.3.1'i Docker ile kendi bilgisayarlarımıza kurduk ve örnek veri yükledik: 5 öğrenci, 2 öğretmen, 3 ders (CS101, CS201, MATH101).
- "Kural" dediğimiz şey, Moodle'ın belli bir durumda ne yaptığı. Örnek: bitişi başlangıcından önce olan bir ders açılamaz.
- Dört adımı slayttan sırayla oku. 1. adımda vurgula: Kodu okuyup tahmin etmiyoruz, çalışan Moodle'da deneyip görüyoruz.
- 3. adımda ekle: Yapay zekâyı her kullandığımızda bunu kaydediyoruz (hangi model, önerisi kabul edildi mi, ne kadar sürdü).
- Bağla: "Bu hafta ilk iki adımı beş modülde yaptık ve 41 kural yazdık. Son iki adımı şimdilik tek bir kuralda yaptık, onu birazdan göstereceğiz."

### 5. Java (Sertug)
> "Yeni sistemi Java ile yazıyoruz. Dil seçerken tek bir soru sorduk: Yapay zekânın yaptığı hatayı kim yakalayacak?"

- Bir araştırmada yapay zekâya gerçek projelerdeki hatalar verilmiş. Java'da yüzde 23'ünü, TypeScript'te yüzde 12'sini, JavaScript'te yüzde 5'ini çözebilmiş.
- Java'da derleyici, birçok hatayı kod daha çalışmadan buluyor.
- Sistemi modüllere ayırdık. Bir modül başka bir modülün iç koduna erişirse otomatik kontrol hata veriyor.
- **Geçiş:** "Kuralları çıkarırken Moodle'da beklemediğimiz şeyler de bulduk. İlkini Hasan anlatacak."

### 6. Şifre (Hasan)
> "Kurduğumuz Moodle'da yanlış şifreyi kaç kez girerseniz girin, hesabınız kilitlenmiyor."

- Hesap kilitleme ayarı kapalı. Bizim hedefimiz: 15 dakikada 5 yanlış şifreden sonra hesabı kilitlemek.
- Hiçbir şey yapmayan kullanıcının oturumu 8 saat açık kalıyor. Hedefimiz en fazla 2 saat.
- İyi haber: Yetkiler doğru çalışıyor. Öğrenci olarak giriş yaptık, adres çubuğundaki dersi elle değiştirdik; kayıtlı olmadığımız derse giremedik, not verme sayfası da açılmadı.
- **Geçiş:** "Güvenlik tarafı böyle. Efekan ders kayıtlarında başka bir şey buldu."

### 7. CS101 / cs101 (Efekan)
> "Moodle'da CS101 kodlu bir ders varken, küçük harfle cs101 kodlu ikinci bir ders açılabiliyor."

- Ders ve ders kaydı kurallarını bir betikle çalışan Moodle'da denedik. 45 kontrolün 45'inde Moodle, yazdığımız kurallara uydu.
- Bu sırada iki zayıflık bulduk. Birincisi: Ders kodunun tekrar etmemesi veritabanında değil, yalnızca PHP kodunda kontrol ediliyor.
- İkincisi: Bitiş tarihi başlangıcından önce olan bir ders kaydı kabul ediliyor, ama hiçbir zaman geçerli olmuyor.
- Yeni sistemde bu iki kuralı veritabanı da zorunlu tutacak. Büyük-küçük harf konusuna ekipçe karar vereceğiz.
- **Geçiş:** "Bunlar tasarım zayıflıkları. Sercan ise Moodle'ın gerçek bir hatasını kendi kurulumumuzda tekrarladı."

### 8. Taslağa geri al (Sercan)
> "Öğretmen bir ödev teslimini taslağa geri aldığında, öğrenciye fazladan bir deneme hakkı açılıyor."

- Önce beklenen: Öğrenci 1. denemesini düzeltir ve yeniden gönderir.
- Ekran görüntüsünü göster: 2. deneme açılmış. Dosya 1. denemede kalmış ve öğrenci onu düzenleyemiyor.
- Bu, Moodle'ın bilinen ve hâlâ açık bir hatası; numarası MDL-87490. Moodle 5.3.1'de tarayıcıda adım adım tekrarladık.
- **Geçiş:** "İkinci hatayı Oğuz anlatacak."

### 9. Kategori 404 (Oğuz)
> "Yönetici 'kullanıcılar tüm dersleri listeleyemesin' diye bir izni kapattığında, öğrenci kayıtlı olduğu derslerin kategorisini de göremiyor."

- student1, CS101 ve CS201'e kayıtlı. Kategori sayfasını açınca 404 hatası alıyor. Yönetici ise her şeyi görüyor.
- Beklenen: Öğrencinin en azından kendi derslerini ve onların kategorisini görmesi.
- Bu da Moodle'ın bilinen ve hâlâ açık bir hatası (MDL-85503). Yazdığımız betik hatayı tek komutla tekrarlıyor.
- Not hesaplama kurallarını da çalışan Moodle'da denedik: 18 kontrolün 18'i geçti. (Örnek sorulursa: 80 ve 40, ağırlıkları 1 ve 3 → ortalama 50.)
- **Geçiş:** "Bunları yeni sistemde düzelteceğiz. Peki 'daha iyi oldu' derken neye göre diyeceğiz? Efekan."

### 10. KPI (Efekan)
> "Yeni sistemi kıyaslayabilmek için önce Moodle'ın bugünkü değerlerini ölçtük."

- Üç örnek, slayttan oku: Moodle'ın kendi testleri incelediğimiz kodun yüzde 31'ini çalıştırıyor, hedefimiz en az yüzde 80. Girişlerin yüzde 95'i 116 milisaniyeden kısa sürüyor, biz daha yavaş olmayacağız. Öğretmen bir ödevi 8 tıkla oluşturuyor, hedefimiz 6.
- Toplam 26 ölçüt belirledik: bakım kolaylığı, güvenlik, hız ve kullanım kolaylığı. Hepsi yedek slaytta.
- Açıkça söyle: Hızı tek bilgisayarda ve az veriyle ölçtük; bu sayılar yalnızca aynı koşulda ölçülen yeni sistemle karşılaştırılabilir.
- **Geçiş:** "Şimdi baştaki öğrenciye, student3'e dönelim. Sertug."

### 11. Demo (Sertug)
> "Baştaki öğrenciyi hatırlayın: student3. Aynı öğrenciyi bu kez yeni sisteme soruyoruz."

Sunumdan önce hazırla: Moodle (`docker compose -f infra/docker-compose.yml up -d`, `./infra/sample-data/load.sh`) ve backend (`cd backend && ./mvnw spring-boot:run`, `POSTGRES_*` ve `SAMPLE_USER_PASSWORD` ayarlı). Terminal yazısını büyüt.

- Yazdığımız ilk kod tek bir soruyu cevaplıyor: "Bu öğrenci bu derse kaydolabilir mi?"
- Komutu çalıştır. Cevabı oku: `allowed: false`, yani kaydolamaz; eksik ön koşul dersi CS101.
- Aynı komutu student1 için çalıştır: `allowed: true`, çünkü student1 CS101'i geçmiş.
- Bu kontrolü 21 otomatik test sınıyor. Henüz derse kayıt işlemine bağlı değil; sıradaki işimiz bu.
- Demo çalışmazsa slayttaki çıktıyı göster ve devam et. Özür dileyip uzatma.
- **Geçiş:** "Peki bu cevabın doğru olduğunu nereden biliyoruz? Oğuz."

### 12. 5/5 (Oğuz)
> "Yeni sistemin kararını beş durumda Moodle'daki bilgiyle karşılaştırdık. Beşinde de uyuşuyor."

- Önce neyi karşılaştırdığımızı söyle: Moodle, öğrencinin CS101'i tamamlayıp tamamlamadığını kaydediyor, ama derse kayıtta buna bakmıyor. Bu yüzden student3'ü CS201'e alıyor. Biz kayıt kararını değil, bu bilgiyi karşılaştırıyoruz.
- Tabloyu satır satır oku: Moodle "tamamladı" diyorsa yeni sistem "kaydolabilir" diyor; "tamamlamadı" diyorsa "kaydolamaz" diyor.
- Son satır yetkisiz istek: student3, student1'i soruyor ve cevap alamıyor. Bu satırda Moodle'a canlı sormadık; Moodle'ın kaynak kodundaki kuralı esas aldık.
- Bu test her kod değişikliğinde otomatik çalışıyor. Sistemi bilerek bozduğumuzda farkı satır satır gösterdi.
- **Geçiş:** "Sertug toparlayacak."

### 13. Kapanış (Sertug)
> "Kısacası: Karşılaştırma düzeni hazır, sıra modülleri yazmakta."

- Bu hafta: 41 kuralı yazdık, 26 ölçütün Moodle'daki değerini ölçtük, ilk kodu yazıp Moodle ile karşılaştırdık.
- Sıradaki: Ön koşul kontrolünü derse kayıt işlemine bağlamak, her kural için bir karşılaştırma testi yazmak, iki Moodle hatasını düzeltmek.
- Eksikleri biz söyleyelim: eğitim planı gereksinimleri, arayüz, bir kullanılabilirlik ölçümü ve ZAP güvenlik taraması henüz yok.
- Hocaya soru: "Yapay zekâ destekli geliştirme ile elle geliştirmeyi hangi alt fonksiyonda karşılaştırmamızı önerirsiniz?" Sonra sus ve bekle.
- Bitiş: "Sorularınızı alabiliriz." Slayt açık kalır.

## Olası sorular

- **Bu çalışmalardaki "testler" ne?** Eski kodun kendi testleri; çeviriyi aynı testlerle sınamışlar. Test olmayan yer kanıtlanamıyor (AlphaTrans'ta %25'in nedeni). Moodle'ın testleri geçiyor ama kodun sadece ~%31'ine dokunuyor; bu yüzden onlarla yetinmiyoruz, aynı girdiyi çalışan Moodle'a ve yeni sisteme verip karşılaştırıyoruz.
- **Script nedir, nasıl çalışır?** "Script, elle tıklamak yerine denemeleri otomatik yapan küçük bir program. İçinde bir deneme listesi var: her denemede Moodle'a ne vereceğimizi ve ne cevap beklediğimizi yazdık. Script Moodle'ın kendi fonksiyonlarını bu girdilerle çağırıyor, gelen cevabı beklenenle karşılaştırıyor, her deneme için PASS ya da FAIL yazıyor. Sonunda yaptığı her şeyi geri alıyor. Ders ve kayıt kurallarında 45 deneme vardı, 45'i de PASS."
- **Neden Java?** Derleyici + modül sınırı testi; yedek slayt "Mimari kararı".
- **YZ'nin yazdığı koda nasıl güveniyorsunuz?** Derleyici, modül testi, arkadaş review'u, sonra çalışan Moodle'a karşı golden master.
- **Performans sayıları neden bu kadar küçük?** Küçük veri, tek makine; sadece aynı koşulda yeni sistemle kıyaslamak için.
- **YZ kabul oranınız ne?** Kayıtların bir kısmı henüz "pending", haftalık raporda vereceğiz.
- **Danışman onayı, kredi sınırı, ders planı?** Moodle'da karşılığı yok; yeni gereksinim olarak sırada. Ders planı ön koşul kontrolünü kullanacak.
