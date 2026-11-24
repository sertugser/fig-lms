---
marp: true
paginate: true
size: 16:9
title: Fig LMS - 1. hafta sunumu
style: |
  section { font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif; font-size: 24px; padding: 48px 64px; color: #1f2933; }
  h1 { font-size: 40px; color: #0b3d62; margin-bottom: 12px; }
  h2 { font-size: 30px; color: #0b3d62; }
  table { font-size: 19px; border-collapse: collapse; }
  th { background: #0b3d62; color: #fff; }
  td, th { padding: 4px 10px; }
  code { font-size: 0.9em; }
  em { color: #52606d; }
  section.lead h1 { font-size: 52px; }
  section.small { font-size: 20px; }
  section.small table { font-size: 17px; }
  .who { position: absolute; top: 18px; right: 64px; font-size: 16px; color: #7b8794; }
  footer { font-size: 14px; color: #9aa5b1; }
footer: Fig LMS · Moodle 5.3.1 modernizasyonu · 1. hafta
---

<!-- _class: lead -->
<!-- _paginate: false -->

# Fig LMS

## Moodle alt fonksiyonlarının yapay zekâ desteğiyle modernizasyonu

1. hafta sunumu · 25 Kasım 2026

Sertug · Hasan · Efekan · Sercan · Oğuz

---

# Bu hafta ne yaptık?

İlk haftanın hedefi **WBS-1** (kapsam ve mevcut sistem analizi) ve **WBS-2'nin başı** (hedef mimari, izlenebilirlik, yapay zekâ kullanım yaklaşımı).

- **Kapsam ve sürüm sabitlendi:** Moodle `v5.3.1` (`cfe123f2`), 6 modül
- **Tekrarlanabilir ortam:** Docker Compose ile Moodle 5.3.1 + PostgreSQL 17 ve örnek veri
- **İş kuralları çıkarıldı:** 5 modül, **41 gereksinim**, her kural çalışan Moodle'da denendi
- **2 Moodle hatası yeniden üretildi:** MDL-87490, MDL-85503 (+ MDL-22648 eksik özellik analizi)
- **SRS ve KPI tablosu:** 26 KPI için Moodle baseline'ı, geliştirmeden **önce** ölçüldü
- **Mimari kararı:** ADR-0001 (Java 21 + Spring Boot 3, modüler monolit)
- **İlk kod ve ilk golden master testi:** ders ön koşulu kontrolü

*Frontend henüz yok; bu hafta analiz ve ölçüm haftasıydı.*

---

# Kapsam

| Modül | Kapsamdaki akışlar |
|---|---|
| Kimlik ve yetki | Giriş, 3 rol (öğrenci, öğretmen, yönetici), ders düzeyinde yetki |
| Ders ve kayıt | Ders oluşturma, kategoriler, öğretmen atama, kayıt |
| Danışmanlık | Ders ön koşulu (MDL-22648), danışman onayı, kredi sınırı |
| Ödev ve teslim | Tarihler, dosya teslimi, geç teslim, taslak ve deneme hakkı |
| Değerlendirme | Not verme, ağırlıklı ortalama, öğrencinin kendi notunu görmesi |
| Eğitim planı (yeni) | Ön koşul ve kredi sınırından dönem dönem ders planı |

**Düzeltilecek Moodle hataları:** MDL-87490 (ödev), MDL-85503 (ders kategorileri).
**Kapsam dışı:** forum, sınav, mesajlaşma ve yukarıdaki akışlar dışındaki eklentiler.

*Analiz edilen Moodle kodu (ilk 5 modül): 165.493 satır PHP, 1.462 üretim dosyası.*

---

# Mimari kararı: ADR-0001

**Java 21 + Spring Boot 3, modüler monolit (Spring Modulith), PostgreSQL, React**

| Kanıt | Kaynak |
|---|---|
| YZ ajanlarının çözdüğü gerçek hata oranı: Java %23,44 · TypeScript %11,61 · JavaScript %5,06 | Multi-SWE-bench (Zan vd., 2025) |
| Tip sistemi LLM çıktısındaki derleme hatalarını yarıdan fazla azaltıyor | Mündler vd., PLDI 2025 |
| Modül sınırları otomatik doğrulanabiliyor (`ApplicationModules.verify()`) | Spring Modulith |
| 5 kişi, 7 hafta: mikroservis altyapısı yerine ölçüme zaman | Fowler, *Monolith First* |

- Her PR'da CI: derleme, testler ve **modül sınırı testi** (`ModularityTest`)
- Sınırlılık: benchmark hata düzeltmeyi ölçüyor, sıfırdan yeniden yazmayı değil. İddiamız "kanıtlar Java'yı destekliyor", "en iyi dil Java" değil.

---

# Literatür ve özgünlük

- YZ ile kod çevirisi kısmen çalışıyor: doğru çeviri oranı **%2,1 – %47,3** (Pan vd., ICSE 2024); depo düzeyinde araçlar kodun yaklaşık **dörtte birinin** davranışını doğrulayabiliyor (AlphaTrans, FSE 2025)
- İlgili çalışmalarda davranış kontrolü: mevcut birim testleri, üretilmiş testler veya tek başına programların fuzzing'i
- Endüstri (Google, Amazon Q): büyük göçler, ama tahmini kazanç ve yalnızca Java'dan Java'ya

**Bizim yöntemimizde yeni olan:**
1. Her YZ destekli değişiklik Moodle kaynağından çıkarılmış bir gereksinim ID'sine (`REQ-<MODÜL>-<NN>`) bağlı ve sonucuyla `docs/ai-log/`'a kaydediliyor
2. Korunan kurallar **çalışan Moodle 5.3.1**'e karşı golden master testiyle kontrol ediliyor (veri durumu, roller, sınır durumları dahil)
3. YZ destekli ve manuel çalışma süre ve review hatası üzerinden; Moodle ve yeni sistem KPI'larla karşılaştırılıyor

---

# Yöntem: bir kural nasıl taşınıyor?

**Moodle kaynağı + çalışan Moodle → gereksinim (REQ ID) → YZ destekli yazım → review → golden master → KPI**

| Adım | Bu haftaki somut çıktı |
|---|---|
| Ortam | `infra/`: Moodle 5.3.1 + PostgreSQL 17, tek komutla örnek veri (5 öğrenci, 2 öğretmen, CS101, CS201, MATH101) |
| Kural çıkarma | Her modülde `verify_rules.php`: kuralları Moodle'ın kendi fonksiyonlarıyla canlı çalıştırıyor |
| Gereksinim | ISO/IEC/IEEE 29148 yapısında SRS, modül başına bir dosya |
| İzlenebilirlik | Gereksinim → issue → kod → test matrisi (`docs/srs/traceability.md`) |
| Uzman kontrol noktası | Her PR en az bir ekip arkadaşının onayıyla merge ediliyor |
| YZ kaydı | Model, verilen bağlam, kabul / değişiklik / ret, süre, review hatası |

---

# SRS ve izlenebilirlik

| Modül | Gereksinim | Sahibi |
|---|---:|---|
| Kimlik ve yetki (`IDN`) | 7 | Hasan |
| Ders ve kayıt (`CRS`) | 9 | Efekan |
| Danışmanlık (`ADV`) | 9 | Sertug |
| Ödev ve teslim (`ASG`) | 8 | Sercan |
| Değerlendirme (`GRD`) | 8 | Oğuz |
| Eğitim planı (`PLN`) | henüz yok | Sertug |
| **Toplam** | **41** + 5 kalite (NFR) | |

Her gereksinim **korunan / hata düzeltmesi / yeni** olarak ayrılıyor; Moodle kaynak yolu ve kabul kriteri yazılı.
İlk uygulanan gereksinim: `REQ-ADV-09` (ders ön koşulu).

---

<div class="who">Hasan</div>

# Kimlik ve yetki

**Üç proje rolü:** öğrenci → `student` · öğretmen → `editingteacher` · yönetici → `manager`

| Ders işlemi | Öğrenci | Öğretmen | Yönetici |
|---|:---:|:---:|:---:|
| Kayıtlı olduğu dersi görme | Evet | Evet | Evet |
| Ödev oluşturma | Hayır | Evet | Evet |
| Ödev notlama | Hayır | Evet | Evet |
| Derse kullanıcı kaydetme | Hayır | Evet | Evet |

**Güvenlik baseline'ı:** kayıtlı olmayan öğrenci CS201'i URL ile açınca kayıt sayfasına yönlendirildi; öğrenci notlama URL'sinde 404 aldı; yanlış şifre oturum açmadı.
**Mevcut ayarlar:** şifre politikası açık (8 karakter + 4 karakter sınıfı), **hesap kilitleme kapalı** (eşik 0), oturum zaman aşımı **8 saat**.

*Yeni sistem kuralı: yetki her istekte ders/etkinlik bağlamında kontrol edilir, yalnızca arayüzde değil.*

---

<!-- _class: small -->

<div class="who">Efekan</div>

# Ders ve kayıt

**Kayıt = üyelik + rol.** Erişim için kaydın **aktif** olması gerekir: askıda değil, yöntem açık, başlangıç geçmiş, bitiş gelmemiş.

| Kural | Moodle sonucu |
|---|---|
| Aynı kısa ad `CS101` | reddedildi |
| `CS101` yanında `cs101` | **kabul edildi** (büyük/küçük harf duyarlı) |
| Bitiş tarihi başlangıçtan önce | reddedildi; eşitlik kabul |
| Öğretmen kayıt yapıyor | *öğrenci* veya *düzenleme yetkisiz öğretmen* verebilir, *düzenleyen öğretmen* veremez |
| Kendi kendine kayıt, limit 2, üçüncü kullanıcı | reddedildi |
| Bitişi başlangıçtan önce olan kayıt | **kabul edildi, ama hiç aktif olmuyor** |

**Canlı Moodle'da 45 kontrol, hepsi geçti.**
**Bulunan boşluklar → REQ-CRS-09:** kısa ad tekilliği yalnızca PHP'de, veritabanında değil; kayıtta ön koşul kontrolü yok (student3 CS101'i geçmeden CS201'de).

---

<!-- _class: small -->

<div class="who">Sertug</div>

# Danışmanlık ve MDL-22648

**Moodle'da danışmanlık modülü yok.** En yakın karşılıklar: erişim kısıtları (etkinlik/bölüm koşulları) ve ders tamamlama. 8 korunan kural, **39 senaryo canlı Moodle'da, hepsi geçti.**

**MDL-22648** "Ön koşul ders tamamlanmadan derse erişimi kısıtla": 2012'de açıldı, hâlâ çözülmedi, **99 oy**, sahibi yok.

Neden hâlâ yok?
1. "Course prerequisites" adı vardı ama yalnızca *tamamlanmayı* etkiliyordu; adı değiştirildi (MDL-22259)
2. Erişim kısıtları ders sınırında duruyor; hiçbir koşul başka bir derse bakamıyor
3. Kayıt eklentilere dağılmış; hiçbiri tamamlanmaya bakmıyor. 2012–2014 topluluk yaması yarım kaldı

**Bizim çözümümüz (REQ-ADV-09):** kural tek yerde, danışmanlık modülünde. Kayıt ve eğitim planı modülü `advising.api` üzerinden çağırıyor. "Tamamlandı" Moodle'daki anlamıyla aynı.

---

<div class="who">Sercan</div>

# Ödev ve MDL-87490

7 korunan kural: teslim penceresi, son tarih ve gecikme, uygunluk ve kilitler, taslak ve son teslim, dosya teslimi, deneme limiti, notlamadan sonra yeniden açma. 1 hata düzeltmesi: **REQ-ASG-08**.

**MDL-87490:** öğretmen notlanmamış bir teslimi **taslağa geri alınca** öğrenciye fazladan bir deneme hakkı veriliyor. Moodle 5.3.1'de yeniden üretildi.

| Adım | Beklenen | Moodle 5.3.1'de görülen |
|---|---|---|
| Öğrenci 1. denemeyi teslim eder | Notlanmayı bekliyor | Notlanmayı bekliyor |
| Öğretmen "taslağa geri al" der | 1. deneme düzenlenebilir taslak kalır | **2. deneme "Reopened"** açıldı, dosya eski denemede kaldı |

**Neden:** geri alma `update_grade()` çağırıyor; not yokken `grade = -1` yer tutucu oluşuyor ve "otomatik" yeniden açma kuralı not verilip verilmediğine bakmıyor.

---

<div class="who">Sercan</div>

# MDL-87490: ekran görüntüsü

![h:430](../../bugs/evidence/MDL-87490-03-student-extra-attempt.png)

*Geri almadan sonra öğrenci görünümü: 2. deneme açılmış, orijinal dosya "Previous attempts" altında. Tüm adımlar ve kanıt: `docs/bugs/MDL-87490.md`.*

---

<!-- _class: small -->

<div class="who">Oğuz</div>

# Değerlendirme ve MDL-85503

**Ağırlıklı ortalama:** toplam = Σ(ağırlık × not) / Σ(ağırlık), notlar önce 0..1'e ölçeklenir. A=80 (ağırlık 1), B=40 (ağırlık 3) → **50**.

| Kural | Moodle sonucu |
|---|---|
| Boş not hariç (varsayılan) / 0 sayılır | 50 / 33,3 (ağırlıklı ortalama) |
| En düşük 1 notu at | 80 |
| Maksimumun üstünde / minimumun altında not | maksimum / minimum olarak saklanır |
| Öğrenci başka öğrencinin notunu istiyor | reddedildi (`nopermissiontoviewgrades`) |

**Canlı Moodle'da 18 kontrol, hepsi geçti.**

**MDL-85503:** `moodle/category:viewcourselist` kapatılınca öğrenci **hiç kategori görmüyor** (HTTP 404), yönetici hepsini görüyor. Kayıtlı olduğu derslerin kategorisi de gizleniyor. Tek komutla yeniden üretiliyor: `python docs/bugs/MDL-85503/reproduce.py`. *Not: hata ders kategorileriyle ilgili, notlarla değil.*

---

<!-- _class: small -->

# KPI'lar: geliştirmeden önce Moodle baseline'ı

| Kategori | KPI sayısı | Moodle 5.3.1 baseline (örnek) | Hedef | Sahibi |
|---|---:|---|---|---|
| Bakım yapılabilirlik | 5 | Ortalama karmaşıklık 3,3–4,8; en yüksek 31–107; test kapsamı %24,3–60,3 (5 modül: %30,8) | ortalama ≤ 3,0; her metot ≤ 15; kapsam ≥ %80 | Oğuz |
| Güvenlik | 11 | Yetkisiz URL erişimi reddedildi; hesap kilitleme kapalı; oturum 8 saat | %100 red; 15 dk'da 5 hata → kilit; oturum ≤ 2 saat | Hasan |
| Performans | 6 | Giriş p95 **115,8 ms**; ders sayfası p95 40,4 ms; 20 eşzamanlı kullanıcıda p95 158,5 ms, %0 hata | Moodle'dan yavaş olmamak | Efekan |
| Kullanılabilirlik | 4 | Tıklama: ödev teslimi 6, not görme 2, ödev oluşturma 8, notlama 7 | 6, 2, 6, 5 | Sercan |

- Ölçüm araçları: lizard + cloc, PHPUnit + PCOV, k6 (3 tekrar, medyan), Moodle'ın kendi yetki fonksiyonları, insan tarafından yapılan akış sayımı
- **Sınırlılıklar:** performans tek makinede ve küçük veriyle (yalnızca aynı koşulda karşılaştırılabilir); güvenlik bir sertifikasyon değil, ZAP taraması yapılmadı; kendi kendine kayıt akışının (FLOW-05) insan ölçümü eksik

---

<div class="who">Sertug</div>

# İlk kod: ders ön koşulu kontrolü (REQ-ADV-09)

`GET /api/advising/prerequisite-check?student=…&course=…` · Java 21 / Spring Boot · **21 test**, CI yeşil

```bash
curl -u student3:$SAMPLE_USER_PASSWORD \
  "http://localhost:8080/api/advising/prerequisite-check?student=student3&course=CS201"
```
```json
{"student":"student3","course":"CS201","allowed":false,
 "reason":"PREREQUISITES_MISSING","missingPrerequisites":["CS101"]}
```

| | Moodle 5.3.1 | Yeni sistem |
|---|---|---|
| student3, CS101'den 40 aldı (geçme notu 50) | **CS201'e kayıtlı** | **reddedildi**, eksik: CS101 |
| Bilinmeyen ders / öğrenci | — | 404 `UNKNOWN_COURSE` / `UNKNOWN_STUDENT` |
| student3 başkası adına soruyor / giriş yok | — | 403 / 401 |

---

<div class="who">Oğuz</div>

# İlk golden master testi: Moodle ile yeni sistem aynı girdiyle

| Senaryo | Girdi | Moodle 5.3.1 | Yeni sistem | Sonuç |
|---|---|---|---|---|
| Tamamlandı | student1 → CS201 (CS101: 85) | CS101 tamamlandı | izin var | AYNI |
| Tamamlanmadı | student5 → CS201 (teslim yok) | tamamlanmadı | izin yok, eksik CS101 | AYNI |
| Sınır | student3 → CS201 (40 < 50) | tamamlanmadı | izin yok, eksik CS101 | AYNI |
| Sınır | student4 → CS201 (notlanmadı) | tamamlanmadı | izin yok, eksik CS101 | AYNI |
| Yetkisiz | student3, student1'i soruyor | `cannotviewreport` | 403 | AYNI |

**5/5 senaryo aynı sonucu veriyor**; test main'de ve her PR'da CI'da çalışıyor (PR #43). Sistem bilerek bozulunca fark satır satır yakalandı (ör. `$.allowed: expected false but was true`).

*Dürüst not: "Yetkisiz" senaryosunda Moodle tarafı canlı çağrı değil, Moodle kaynak kodundaki kural; test token'ı admin'e ait. Bilinçli fark: Moodle student3'ü CS201'e alıyor, yeni sistem almıyor.*

---

# Yapay zekâ kullanım kaydı

- Her YZ destekli PR için `docs/ai-log/` altında bir kayıt: model ve sürüm, verilen bağlam, **kabul / değiştirildi / reddedildi**, aktif süre, review'da bulunan hata
- Bu hafta **18 görev kaydı** açıldı. Karar verilenlerde çoğunluk "değiştirildi": YZ taslak üretti, kurallar canlı Moodle'da denenip düzeltildi. Bir kısmı hâlâ "pending", bu yüzden **kabul oranını henüz vermiyoruz**
- Ölçüm kuralları önceden yazıldı (`docs/ai-log/README.md`): bekleme süresi sayılmaz, "YZ kabul oranı" formülü sabit
- PR şablonunda zorunlu YZ bölümü; YZ çıktısı review olmadan merge edilmiyor

**Neden önemli:** WBS-3'teki YZ destekli ve manuel geliştirme karşılaştırmasının (emek, hata, yeniden çalışma) verisi buradan gelecek.

---

# Eksikler ve sonraki adımlar

**Eksik / açık kalanlar**
- Eğitim planı modülünün gereksinimleri henüz yazılmadı
- Danışman onayı ve kredi sınırı: Moodle karşılığı yok, gereksinimleri sonra
- Frontend yok; FLOW-05 insan ölçümü ve ZAP taraması yapılmadı

**Sonraki adımlar (WBS-2 → WBS-3)**
- Ön koşul kuralını ders modülünün kayıt akışına bağlamak; öğretmen / danışman istisnası
- Modül modül yeniden geliştirme: her korunan kural için golden master testi
- MDL-87490 ve MDL-85503 için önce Moodle davranışında başarısız olan test, sonra düzeltme
- İlk YZ destekli ve manuel karşılaştırma için alt fonksiyon seçimi

---

<!-- _class: lead -->
<!-- _paginate: false -->

# Teşekkürler

Sorular?

Depo: `github.com/sertugser/fig-lms` · SRS: `docs/srs/` · KPI: `docs/srs/kpi.md` · Hatalar: `docs/bugs/`
