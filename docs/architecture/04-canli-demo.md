# 4. Canlı gösterim senaryosu (2. hafta sunumu)

Amaç: Hocaya üç şeyi **gözünün önünde** göstermek:
1. Moodle'ı kurduk ve çalışıyor; CS101'i bitirmemiş öğrenci CS201'e kaydolabiliyor.
2. Bizim sistem aynı soruya "giremez, eksik CS101" diyor ve diğer her şeyde Moodle ile aynı cevabı veriyor.
3. Modül sınırını bozmaya çalışınca sistem buna izin vermiyor.

Bilgisayar: Windows + Docker Desktop. Komutlar **Git Bash**'te çalıştırılacak şekilde yazıldı (PowerShell farkları ayrıca verildi). Bütün komutlar repo kökünden (`fig-lms/`).

İşaretler: ✅ bu repoda denendi · ⚠️ sizin makinenizde provada denenmeli (Moodle ekranı bu oturumda açılamadı; adımlar Moodle 5.3.1 kaynak kodundaki menü yazılarına ve ekibin önceki ekran görüntülerine göre yazıldı)

---

## Sunumdan bir gün önce: prova (30 dk)

### Hazırlık 1: Moodle'ı aç
```bash
git pull
cp -n infra/.env.example infra/.env        # yoksa oluşturur; şifreleri bir kere doldur
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml logs -f moodle   # "MOODLE_WS_TOKEN=..." görünce Ctrl+C
./infra/sample-data/load.sh
docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php admin/cli/scheduled_task.php --execute='\core\task\completion_regular_task'
```
Kontrol: http://localhost:8000 açılıyor, `admin` + `MOODLE_ADMIN_PASSWORD` ile giriliyor.

### Hazırlık 2: CS201'e "kendi kendine kayıt" ekle ⚠️
Örnek veride sadece "manuel kayıt" var, bu yüzden öğrenci kendisi kaydolamıyor (ekip ekran görüntüsü `docs/srs/modules/course/screenshots/07-not-enrolled-student-cs201.png`). Admin olarak:
1. **Data Structures (CS201)** dersine gir → **Participants** sekmesi.
2. Sol üstteki açılır menüden (**Enrolled users** yazan) **Enrolment methods**'u seç.
3. **Add method** açılır menüsünden **Self enrolment**'ı seç.
4. Formda:
   - **Keep current self enrolments active** → **Yes** (varsayılanı **No**; böyle bırakırsanız yöntem kapalı olur ✅ kaynak: `enrol/self/settings.php:65-66`)
   - **Allow new self enrolments** → **Yes**
   - **Enrolment key** → boş bırak (şifre olursa açılır pencere çıkar, demo uzar)
5. **Add method**.

### Hazırlık 3: Yeni sistemi (backend) aç ✅
Backend'in kendi veritabanı var (Moodle'ınkinden ayrı):
```bash
docker run -d --name fig-demo-db -p 55432:5432 -e POSTGRES_DB=fig -e POSTGRES_USER=fig -e POSTGRES_PASSWORD=demo postgres:17
cd backend
POSTGRES_HOST=localhost POSTGRES_PORT=55432 POSTGRES_PASSWORD=demo SAMPLE_USER_PASSWORD=demo ./mvnw spring-boot:run
```
PowerShell'de:
```powershell
docker run -d --name fig-demo-db -p 55432:5432 -e POSTGRES_DB=fig -e POSTGRES_USER=fig -e POSTGRES_PASSWORD=demo postgres:17
cd backend
$env:POSTGRES_HOST="localhost"; $env:POSTGRES_PORT="55432"; $env:POSTGRES_PASSWORD="demo"; $env:SAMPLE_USER_PASSWORD="demo"
.\mvnw.cmd spring-boot:run
```
Kontrol (yeni bir terminalde):
```bash
curl -u student1:demo "http://localhost:8080/api/advising/prerequisite-check?student=student1&course=CS201"
# {"student":"student1","course":"CS201","allowed":true,"reason":"PREREQUISITES_COMPLETED","missingPrerequisites":[]}
```
PowerShell'de `curl` yerine `curl.exe` yazın.

### Hazırlık 4: Ekranı düzenle
Sunumda açık olacaklar, bu sırayla:
1. **Diyagram sayfası**: `docs/presentation/2026-12-week2-mimari/index.html` (çift tıkla, tarayıcıda açılır, internet gerekmez).
2. **Tarayıcı penceresi A**: Moodle, `admin` olarak giriş yapılmış, CS201 dersinde.
3. **Gizli pencere B**: Moodle giriş sayfası (`student5` için).
4. **Terminal**: repo kökünde, büyük yazı tipi (Ctrl + ile büyüt).

### Provadan sonra temizle ⚠️
Provada student5 CS201'e kaydolduysa sunumdan önce çıkarın: admin → CS201 → Participants → Emre Dogan satırında çöp kutusu (Self enrolment) → Unenrol. Yoksa sunumda "zaten kayıtlı" görünür.

---

## Sunum akışı (yaklaşık 10 dakika)

| # | Ne gösteriliyor | Ekran | Süre |
|---|---|---|---|
| 1 | Moodle büyük resim + eklenti yapısı | Diyagram 1, 2 | 1,5 dk |
| 2 | Moodle'da öğrenci kendini kaydediyor | Moodle (canlı) + Diyagram 3 | 2 dk |
| 3 | 5 modülün Moodle'daki yeri | Diyagram 4 | 1 dk |
| 4 | Yeni sistem + aynı soru | Diyagram 5, 6 + terminal | 2 dk |
| 5 | Kuralı bozmayı dene | Terminal + Diyagram 7 | 1,5 dk |
| 6 | Golden master | Terminal + Diyagram 10 | 1,5 dk |
| 7 | Yan yana özet | Diyagram 8 + tablo | 0,5 dk |

### Adım 1: "Moodle nasıl bir program?"
Diyagram 1'i aç. Söyle:
> "Moodle'ı Docker'da kurduk, şu an bu bilgisayarda çalışıyor. Moodle tek parça bir PHP programı. Her adres bir PHP dosyası. Her dosya önce ortak ayarları yüklüyor, sonra herkes herkesin fonksiyonunu çağırıp aynı veritabanına yazabiliyor."

Diyagram 2:
> "Moodle'ın her özelliği bir eklenti. Sadece derse kayıt için 13 eklenti var. Örneğin öğrencinin kendisini kaydetmesi `enrol/self` klasöründe."

### Adım 2: Moodle'da canlı kayıt ⚠️
1. Pencere A (admin): CS201 → **Course completion** ayarlarını göster (Ders sayfası → **More** → **Course completion**; doğrudan adres `http://localhost:8000/course/completion.php?id=<CS201 id>`).
   > "Burada 'CS101 tamamlanmış olmalı' kuralı var. Ama bu kural sadece CS201'in **bitirilmesini** etkiliyor, derse **girişi** değil."
2. Pencere B (gizli): `student5` / `SAMPLE_USER_PASSWORD` ile giriş. (student5 = Emre Dogan; CS101 ödevini teslim etmedi.)
3. Adres çubuğuna `http://localhost:8000/course/search.php?search=CS201` yaz ya da dersler listesinden **Data Structures**'a tıkla.
4. **Self enrolment** kutusunda **Enrol me** düğmesine bas.
5. Ekranda "You are enrolled in the course." çıkar, öğrenci dersin içindedir.
   > "CS101'i bitirmemiş öğrenci CS201'e girdi. Moodle hiç sormadı."
6. Diyagram 3'e geç ve adımları göster:
   > "Az önce bastığım düğme şu yoldan geçti: `enrol/index.php`, sonra kendi kendine kayıt eklentisi kontrollerini yaptı: tarih, kontenjan, yetki. Sonra `enrol_user()` veritabanına iki satır yazdı. Bu yolun hiçbir yerinde 'CS101 bitti mi' sorusu yok. Moodle'da bu istek 2012'den beri açık: MDL-22648."

### Adım 3: "Seçtiğimiz 5 modül Moodle'ın neresinde?"
Diyagram 4:
> "Projede yeniden yazdığımız beş bölüm Moodle'da bu klasörlerde. Oklar, bir bölümün kaç dosyasının diğerinin tablosuna ya da fonksiyonuna doğrudan dokunduğunu gösteriyor. Bunu kendi yazdığımız küçük bir script ile saydık. Gördüğünüz gibi her şey birbirine bağlı."

### Adım 4: Yeni sistem ve aynı soru ✅
Diyagram 5:
> "Bizim sistem de tek program, ama içi altı odaya bölünmüş. Her odanın bir vitrini var: `api`. Başka oda sadece vitrinden alışveriş yapabilir."

Terminale geç, **aynı öğrenci, aynı ders**:
```bash
curl -u student5:demo "http://localhost:8080/api/advising/prerequisite-check?student=student5&course=CS201"
```
Beklenen:
```json
{"student":"student5","course":"CS201","allowed":false,"reason":"PREREQUISITES_MISSING","missingPrerequisites":["CS101"]}
```
> "Moodle 'gir' dedi; bizim sistem 'giremez, eksik ders CS101' diyor."

CS101'i geçmiş öğrenci:
```bash
curl -u student1:demo "http://localhost:8080/api/advising/prerequisite-check?student=student1&course=CS201"
```
Beklenen: `"allowed":true,"reason":"PREREQUISITES_COMPLETED"`.

Güvenlik: öğrenci başkasının durumunu soruyor:
```bash
curl -i -u student3:demo "http://localhost:8080/api/advising/prerequisite-check?student=student1&course=CS201"
```
Beklenen: `HTTP/1.1 403`.
> "Öğrenci sadece kendi durumunu sorabilir. Moodle'da da kural aynı."

Diyagram 6'yı açıp isteğin yolunu göster: güvenlik → controller → api → servis → repository → veritabanı.

### Adım 5: Kuralı bozmayı dene ✅
> "Diyelim ki biri, ya da yapay zekâ, kestirme yoldan ders modülünden danışmanlığın veritabanı sınıfına doğrudan erişmek istedi."

Git Bash'te (repo kökü):
```bash
cat > backend/src/main/java/com/figlms/course/internal/EnrolmentShortcut.java <<'EOF'
package com.figlms.course.internal;

import com.figlms.advising.internal.AdvisingRepository;

/** DEMO: the course module reads the advising tables directly, skipping the advising api. */
class EnrolmentShortcut {
    AdvisingRepository repository;
}
EOF
cd backend && ./mvnw -q compile; cd ..
```
Beklenen hata:
```text
[ERROR] .../course/internal/EnrolmentShortcut.java:[3,36] com.figlms.advising.internal.AdvisingRepository is not public in com.figlms.advising.internal; cannot be accessed from outside package
```
> "Derlenmedi bile. Diyelim ki biri o sınıfı `public` yaptı; o zaman da `ModularityTest` yakalıyor: 'course modülü advising'in gizli sınıfına bağımlı'. Bu test her PR'da çalışıyor, kırmızıysa kod birleşmiyor."

Hemen sil:
```bash
rm backend/src/main/java/com/figlms/course/internal/EnrolmentShortcut.java
```
(İkinci kat korumanın, yani `ModularityTest` hatasının metni Diyagram 7'de. Canlı göstermek isterseniz `advising/internal` içine boş bir `public class AdvisingTables {}` ekleyip `EnrolmentShortcut` içinde onu kullanın, sonra `./mvnw -q test -Dtest=ModularityTest`. İkisini de sonra silin.)

### Adım 6: Golden master ✅
> "Yapay zekâ kodu yazdı ama doğru mu? Bunu Moodle ile karşılaştırarak kanıtlıyoruz."

Backend çalışırken, ikinci terminalde:
```bash
cd tests/golden-master
FIG_BACKEND_URL=http://localhost:8080 SAMPLE_USER_PASSWORD=demo ./mvnw -q test -Dtest=PrerequisiteGoldenMasterTest -Dgolden.requireBackend=true
```
Sonunda tablo çıkar: `5/5 scenarios give the same result in Moodle and the new system.`
- `infra/.env` içinde `MOODLE_WS_TOKEN` doluysa Moodle tarafı **canlı** Moodle'dan okunur (tabloda `[live]`), değilse repodaki kayıtlı cevaplardan (`[snapshot]`).
- Backend açık değilse tek komutluk sürüm: `./run-prerequisite-golden-master.sh` (kendi veritabanını ve backend'ini açıp kapatır, 1-2 dk sürer).

Diyagram 10'u göster:
> "Aynı örnek veriyi iki sisteme veriyoruz, sonuçları makine karşılaştırıyor. Bilerek farklı olan tek şey ön koşul kuralı, o da Moodle'ın eksiği."

### Adım 7: Özet
Diyagram 8 ve tabloyu göster. Bitiriş cümlesi:
> "Moodle'ı kurduk, kodunu okuduk, bir isteğin hangi dosyalardan geçtiğini gösterebiliyoruz. Yeni sistemde aynı istek daha kısa ve kuralları tek yerde; Moodle ile aynı sonucu verdiğini de testle kanıtlıyoruz."

---

## Sunumdan sonra kapat
```bash
# backend terminalinde Ctrl+C
docker rm -f fig-demo-db
docker compose -f infra/docker-compose.yml stop
```

## Bir şey ters giderse
| Sorun | Çözüm |
|---|---|
| Moodle açılmıyor | `docker compose -f infra/docker-compose.yml ps`; `logs moodle`. İlk kurulum 5 dk sürer. |
| "Enrol me" düğmesi yok | Yöntem kapalı: Hazırlık 2, adım 4'teki iki ayar **Yes** olmalı. Ya da student5 zaten kayıtlı ("Provadan sonra temizle"). |
| curl `401` | Şifre yanlış ya da backend `SAMPLE_USER_PASSWORD` olmadan açıldı. |
| Backend açılmıyor, veritabanı hatası | `docker ps` ile `fig-demo-db` çalışıyor mu bak; port 55432 başka şeyle dolu olabilir. |
| Golden master "skipped" | Backend kapalı ya da `FIG_BACKEND_URL` yanlış. |
| Canlı demo tamamen çöktü | Diyagram sayfasındaki "Gerçek cevaplar" kutuları 25 Kasım'da alınmış gerçek çıktılar; onlardan anlatın. |
