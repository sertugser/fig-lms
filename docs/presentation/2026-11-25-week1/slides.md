---
marp: true
size: 16:9
title: Fig LMS - 1. hafta
style: |
  section { font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif; font-size: 30px; padding: 56px 72px; color: #1f2933; background: #fff; justify-content: flex-start; }
  h1 { font-size: 44px; line-height: 1.2; color: #0b3d62; margin: 0 0 28px 0; }
  h2 { font-size: 30px; color: #52606d; font-weight: 400; }
  strong { color: #0b3d62; }
  code { font-size: 0.8em; }
  pre { font-size: 22px; }
  table { font-size: 22px; border-collapse: collapse; }
  th { background: #0b3d62; color: #fff; }
  td, th { padding: 6px 14px; }
  .big { font-size: 120px; font-weight: 700; color: #0b3d62; line-height: 1; }
  .mid { font-size: 64px; font-weight: 700; color: #0b3d62; line-height: 1.05; }
  .red { color: #b42318; }
  .green { color: #1a7f37; }
  .muted { color: #7b8794; font-size: 22px; }
  .row { display: flex; gap: 32px; align-items: stretch; }
  .card { flex: 1; border: 2px solid #d9e2ec; border-radius: 14px; padding: 22px 24px; }
  .card p { margin: 8px 0 0 0; font-size: 24px; }
  .step { flex: 1; background: #f0f4f8; border-radius: 12px; padding: 18px; font-size: 24px; text-align: center; }
  .arrow { align-self: center; font-size: 40px; color: #9aa5b1; }
  .who { position: absolute; top: 20px; right: 72px; font-size: 18px; color: #9aa5b1; }
  section.title { justify-content: center; }
  section.title h1 { font-size: 60px; }
  section.backup h1 { font-size: 34px; }
  section.backup { font-size: 22px; }
  section.backup::after { content: "Yedek"; position: absolute; top: 20px; right: 72px; font-size: 18px; color: #9aa5b1; }
---

<!-- _class: title -->

# Moodle'ı yapay zekâyla yeniden yazmak kolay.<br>Aynı şekilde çalıştığını kanıtlamak zor.

## Fig LMS · 1. hafta · 25 Kasım 2026

<p class="muted">Sertug · Hasan · Efekan · Sercan · Oğuz</p>

---

<div class="who">Sertug</div>

# student3 CS101'den kaldı. Moodle onu yine de CS201'e kaydetti.

<div class="row">
<div class="card"><div class="mid red">40</div><p>CS101 notu<br>(geçme notu 50)</p></div>
<div class="card"><div class="mid red">Kayıtlı</div><p>CS201'de, Moodle 5.3.1</p></div>
<div class="card"><div class="mid">2012</div><p>Ön koşul isteği o yıldan beri açık<br>(MDL-22648, 99 oy)</p></div>
</div>

<p class="muted">Kaynak: infra/sample-data, docs/srs/modules/advising.md</p>

---

<div class="who">Sertug</div>

# Yapay zekânın başka dile çevirdiği kod çoğu zaman derleniyor ama doğru çalışmıyor

<div class="row">
<div class="card"><div class="mid">En fazla %47</div><p>Çevirinin testleri geçtiği oran. Kalanında hata var; çalışma 1.748 hatayı 15 türde sınıflandırmış.<br><span class="muted">1.700 örnek, 5 dil, birkaç LLM (Pan vd., ICSE 2024)</span></p></div>
<div class="card"><div class="mid">%96 → %25</div><p>Bütün bir projeyi çevirince kodun %96'sı derleniyor, ama ancak %25'inin doğru çalıştığı kanıtlanabiliyor.<br><span class="muted">10 Java projesi → Python (AlphaTrans, FSE 2025)</span></p></div>
</div>

<br>

Bu yüzden bizim için asıl soru şu: **yeni sistemin Moodle ile aynı davrandığını nasıl kanıtlarız?**

---

<div class="who">Sertug</div>

# Önce Moodle'ı ölçüyoruz, sonra yazıyoruz, sonra aynı girdiyle karşılaştırıyoruz

<div class="row">
<div class="step"><strong>1</strong><br>Kuralı çalışan Moodle'da dene</div>
<div class="arrow">→</div>
<div class="step"><strong>2</strong><br>Gereksinim yaz<br><code>REQ-ADV-09</code></div>
<div class="arrow">→</div>
<div class="step"><strong>3</strong><br>YZ ile yaz, arkadaşın incelesin</div>
<div class="arrow">→</div>
<div class="step"><strong>4</strong><br>Aynı girdiyi iki sisteme ver, karşılaştır</div>
</div>

<br>

Bu hafta: Moodle 5.3.1'i Docker'da kurduk, 5 modülde **41 kural** çıkardık, hepsini canlı Moodle'da denedik.
Her YZ kullanımı kayıt altında: model, verilen bağlam, ne kadarını değiştirdik, ne kadar sürdü.

---

<div class="who">Sertug</div>

# Java seçtik, çünkü derleyici YZ'nin hatalarını bizden önce yakalıyor

<div class="row">
<div class="card"><div class="mid">%23,4</div><p>Java</p></div>
<div class="card"><div class="mid" style="color:#7b8794">%11,6</div><p>TypeScript</p></div>
<div class="card"><div class="mid" style="color:#9aa5b1">%5,1</div><p>JavaScript</p></div>
</div>

<p class="muted">YZ ajanlarının gerçek hataları çözme oranı, Multi-SWE-bench (Zan vd., 2025)</p>

Java 21 + Spring Boot, tek parça ama modüllere bölünmüş. Bir modül diğerinin içine erişirse **CI kırılıyor**.

---

<div class="who">Hasan</div>

# Moodle'da yanlış şifreyi istediğiniz kadar deneyebilirsiniz

<div class="row">
<div class="card"><div class="big red">0</div><p>Hesap kilitleme eşiği: kilit kapalı<br>Hedefimiz: 15 dakikada 5 hata → kilit</p></div>
<div class="card"><div class="big">8 sa</div><p>Boşta kalan oturumun süresi<br>Hedefimiz: en fazla 2 saat</p></div>
</div>

<br>

İyi haber: yetkiler doğru çalışıyor. Öğrenci URL'yi değiştirip başka derse giremedi, not verme sayfası 404 döndü.

---

<div class="who">Efekan</div>

# Moodle "CS101" ile "cs101"i iki farklı ders sayıyor

<div class="row">
<div class="card"><div class="mid">45 / 45</div><p>ders ve kayıt kuralı canlı Moodle'da doğrulandı</p></div>
<div class="card"><div class="mid red">Sadece PHP'de</div><p>Ders kodu tekilliği veritabanında değil, yalnızca PHP kodunda kontrol ediliyor</p></div>
</div>

<br>

Bitişi başlangıcından önce olan bir kayıt da kabul ediliyor, ama hiçbir zaman aktif olmuyor.
Yeni sistemde bu kurallar **veritabanında** zorunlu olacak.

---

<div class="who">Sercan</div>

# Öğretmen "taslağa geri al" dediğinde öğrenci fazladan bir hak kazanıyor

![h:400](../../bugs/evidence/MDL-87490-03-student-extra-attempt.png)

<p class="muted">MDL-87490, Moodle 5.3.1'de yeniden üretildi. Beklenen: 1. deneme düzenlenebilir kalsın. Görülen: 2. deneme açıldı.</p>

---

<div class="who">Oğuz</div>

# Kategori izni kapatılınca öğrenci kendi dersini bile bulamıyor

<div class="row">
<div class="card"><p><strong>student1</strong> (CS101 ve CS201'e kayıtlı)</p><div class="mid red">HTTP 404</div><p>Hiç kategori görmüyor</p></div>
<div class="card"><p><strong>admin</strong></p><div class="mid green">Hepsi</div><p>Engineering ve tüm dersler</p></div>
</div>

<br>

MDL-85503 tek komutla yeniden üretiliyor. Not hesaplama tarafında da **18 kural** canlı Moodle'da doğrulandı (ağırlıklı ortalama, en düşük notu atma, sınırlar).

---

<div class="who">Efekan</div>

# Yeni sistemi neyle kıyaslayacağımızı yazmaya başlamadan ölçtük

<div class="row">
<div class="card"><div class="mid">%30,8</div><p>Moodle kodunun testlerin çalıştırdığı kısmı (kapsam)<br>Hedef: ≥ %80</p></div>
<div class="card"><div class="mid">115,8 ms</div><p>Giriş, p95<br>Hedef: daha yavaş olmamak</p></div>
<div class="card"><div class="mid">8 tık</div><p>Ödev oluşturma<br>Hedef: 6</p></div>
</div>

<br>

Toplam **26 KPI**: bakım yapılabilirlik, güvenlik, performans, kullanılabilirlik.
<p class="muted">Performans tek makinede ve küçük veriyle ölçüldü; sadece aynı koşulda karşılaştırılabilir.</p>

---

<div class="who">Sertug · canlı demo</div>

# Moodle student3'ü kaydediyor, bizim sistem "önce CS101" diyor

```bash
curl -u student3:*** ".../api/advising/prerequisite-check?student=student3&course=CS201"
```
```json
{ "allowed": false,
  "reason": "PREREQUISITES_MISSING",
  "missingPrerequisites": ["CS101"] }
```

Kural tek yerde, danışmanlık modülünde. Kayıt ve ders planı onu çağırıyor. 21 test ile geliyor.

---

<div class="who">Oğuz</div>

# "CS101'i tamamladı mı?" sorusuna Moodle ile 5 durumun 5'inde aynı cevabı veriyoruz

<div class="row">
<div class="card" style="flex:0 0 32%"><div class="big green">5 / 5</div><p>senaryo aynı sonuç<br>main'de, her PR'da CI'da</p></div>
<div class="card">

| Senaryo | Sonuç |
|---|---|
| CS101'i geçmiş | izin var, Moodle ile aynı |
| Hiç teslim etmemiş | izin yok, aynı |
| 40 almış (sınır) | izin yok, aynı |
| Notlanmamış (sınır) | izin yok, aynı |
| Başkasını soruyor | reddedildi, aynı |

</div>
</div>

<p class="muted">Sistemi bilerek bozduk, test farkı satır satır yakaladı. "Başkasını soruyor" senaryosunda Moodle tarafı canlı çağrı değil, kaynak koddaki kural.</p>

---

<div class="who">Sertug</div>

# Kanıt altyapısı hazır, şimdi modülleri yazmaya geçiyoruz

<div class="row">
<div class="card"><p><strong>Bu hafta</strong></p><p>41 kural canlı Moodle'da doğrulandı<br>26 KPI'ın başlangıç değeri ölçüldü<br>İlk endpoint ve ilk golden master testi: 5/5</p></div>
<div class="card"><p><strong>Sıradaki</strong></p><p>Ön koşulu kayıt akışına bağlamak<br>Her korunan kurala bir golden master testi<br>İki Moodle hatası için önce başarısız test, sonra düzeltme</p></div>
</div>

<br>

**Sizin görüşünüzü istediğimiz konu:** YZ destekli ve manuel geliştirmeyi hangi alt fonksiyonda karşılaştıralım?

<p class="muted">Henüz yok: eğitim planı gereksinimleri, arayüz, kendi kendine kayıt akışının insan ölçümü, ZAP taraması.</p>

---

<!-- _class: backup -->

# Kapsam: 6 modül

| Modül | Kapsamdaki akışlar | Gereksinim | Sahibi |
|---|---|---:|---|
| Kimlik ve yetki | Giriş, 3 rol, ders düzeyinde yetki | 7 | Hasan |
| Ders ve kayıt | Ders, kategori, öğretmen atama, kayıt | 9 | Efekan |
| Danışmanlık | Ön koşul (MDL-22648), danışman onayı, kredi sınırı | 9 | Sertug |
| Ödev ve teslim | Tarihler, dosya teslimi, geç teslim, deneme hakkı | 8 | Sercan |
| Değerlendirme | Not verme, ağırlıklı ortalama, kendi notunu görme | 8 | Oğuz |
| Eğitim planı (yeni) | Ön koşul ve kredi sınırından dönem planı | henüz yok | Sertug |

Moodle sürümü `v5.3.1` (`cfe123f2`). Analiz edilen kod (ilk 5 modül): 165.493 satır PHP. SRS ISO/IEC/IEEE 29148 yapısında, gereksinim → issue → kod → test izlenebilirlik matrisi `docs/srs/traceability.md`.

---

<!-- _class: backup -->

# KPI baseline (Moodle 5.3.1)

| Kategori | KPI | Moodle | Hedef |
|---|---:|---|---|
| Bakım yapılabilirlik | 5 | Ort. karmaşıklık 3,3–4,8; en yüksek 31–107; test kapsamı %24,3–60,3 (5 modül %30,8) | ort. ≤ 3,0; her metot ≤ 15; kapsam ≥ %80 |
| Güvenlik | 11 | Yetkisiz URL reddedildi; kilit kapalı; oturum 8 saat | %100 red; 15 dk'da 5 hata → kilit; oturum ≤ 2 saat |
| Performans | 6 | Giriş p95 115,8 ms; ders sayfası p95 40,4 ms; 20 eşzamanlı kullanıcı p95 158,5 ms, %0 hata | Moodle'dan yavaş olmamak |
| Kullanılabilirlik | 4 | Tık: ödev teslimi 6, not görme 2, ödev oluşturma 8, notlama 7 | 6, 2, 6, 5 |

Araçlar: lizard, cloc, PHPUnit + PCOV, k6 (3 tekrar, medyan), Moodle'ın yetki fonksiyonları, insan tarafından yapılan akış sayımı. Güvenlik bir sertifikasyon değil; ZAP taraması yapılmadı. Tüm tablo: `docs/srs/kpi.md`.

---

<!-- _class: backup -->

# Mimari kararı ve YZ kullanım kaydı

**ADR-0001:** Java 21 + Spring Boot 3, modüler monolit (Spring Modulith), PostgreSQL, React.
- Tip sistemi LLM çıktısındaki derleme hatalarını yarıdan fazla azaltıyor (Mündler vd., PLDI 2025)
- `ApplicationModules.verify()` her PR'da modül sınırlarını kontrol ediyor
- 5 kişi, 7 hafta: mikroservis yerine tek parça (Fowler, *Monolith First*)
- Sınırlılık: benchmark hata düzeltmeyi ölçüyor, sıfırdan yazmayı değil

**YZ kaydı (`docs/ai-log/`):** bu hafta 18 görev kaydı. Her kayıtta model, bağlam, kabul / değiştirildi / reddedildi, süre ve review hatası var. Bazı kayıtlar hâlâ "pending" olduğu için kabul oranını henüz vermiyoruz.
