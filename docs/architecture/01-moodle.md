# 1. Moodle bugün nasıl çalışıyor? (eski sistem)

Bu dosyadaki her bilgi **Moodle 5.3.1** kaynak kodundan (`git clone -b v5.3.1 https://github.com/moodle/moodle.git`) kontrol edildi. Dosya yolları Moodle'ın `public/` klasörüne göredir; satır numaraları `v5.3.1` etiketindeki satırlardır. Kontrol edilmemiş ya da tahmin olan her şey **⚠️** ile işaretli.

İşaretler: ✅ kaynak koddan doğrulandı · 📏 bizim script'imizle sayıldı (yöntem altta) · ⚠️ doğrulanmadı / tahmin

---

## Diyagram 1: Moodle büyük resim

```mermaid
flowchart TB
    U["👤 Tarayıcı<br/>öğrenci, öğretmen, yönetici"]

    subgraph WEB["Web sunucusu: public/"]
        P["Sayfa dosyaları<br/>her adres = bir PHP dosyası<br/>course/view.php · enrol/index.php · mod/assign/view.php"]
        WS["Web servis kapısı<br/>webservice/rest/server.php"]
    end

    subgraph BOOT["Her istekte ilk iş"]
        G["config.php → lib/setup.php<br/>global değişkenleri kurar<br/>$CFG ayarlar · $DB veritabanı · $USER giriş yapan kişi<br/>$PAGE sayfa bilgisi · $OUTPUT ekranı çizen nesne"]
    end

    subgraph CODE["Herkes herkesi çağırabilir"]
        L["Çekirdek kütüphaneler · lib/<br/>moodlelib.php · accesslib.php yetki<br/>enrollib.php kayıt · gradelib.php not<br/>completionlib.php tamamlama"]
        PL["Eklentiler · plugin<br/>45 tür, 327 eklenti<br/>mod/assign · enrol/self · auth/manual<br/>availability/condition/*"]
    end

    CRON["Zamanlanmış görevler · cron<br/>admin/cli/cron.php<br/>ör. ders tamamlamayı hesaplar"]
    T["Tema ve şablonlar<br/>theme/boost · Mustache"]
    DB[("Tek ortak PostgreSQL<br/>~500 tablo, hepsi mdl_ önekli")]
    F[("moodledata/<br/>yüklenen dosyalar")]

    U --> P
    U --> WS
    P --> G
    WS --> G
    G --> L
    G --> PL
    L <--> PL
    L -->|"$DB->get_record() / insert_record()"| DB
    PL -->|"$DB->... doğrudan"| DB
    PL --> F
    CRON --> L
    L -->|"$OUTPUT->header()"| T
    T -->|HTML| U
```

**Bunu şöyle anlat:**
> "Moodle tek parça büyük bir PHP programı. Tarayıcıda açtığınız her adres aslında bir PHP dosyası: `enrol/index.php` açarsanız o dosya çalışır. Her dosya ilk satırda `config.php`'yi çağırır; bu da ortak 'global' değişkenleri kurar: veritabanı `$DB`, giriş yapan kişi `$USER`, ekran `$OUTPUT`. Bundan sonra kodun herhangi bir yeri, başka herhangi bir yerin fonksiyonunu çağırabilir ve veritabanındaki herhangi bir tabloya doğrudan yazabilir. Bütün modüller tek bir ortak veritabanını paylaşıyor."
>
> Benzetme: Herkesin her odaya girebildiği, tek bir ortak deposu olan büyük bir ev.

**Kanıt:**
- ✅ `enrol/index.php:25` `require('../config.php');`, `public/config.php` kök `config.php`'yi, o da `lib/setup.php`'yi yükler (`config-dist.php` son satır).
- ✅ `lib/setup.php`: `$OUTPUT = new bootstrap_renderer();` (satır 653), `setup_DB();` (691), `\core\session\manager::start();` (905), `$PAGE = new $classname();` (1049). `$DB`, `$USER`, `$SESSION`, `$COURSE` burada `global` olarak tanımlanır (449-515).
- ✅ Veritabanı sürücüsü: `lib/dml/pgsql_native_moodle_database.php`; önek `mdl_` (`infra/moodle/config.php:15`).
- 📏 45 eklenti türü ve 80 alt sistem `lib/components.json`'da tanımlı; bu türlerin klasörlerinde `version.php`'si olan 327 eklenti var (alt eklentiler, ör. `assignsubmission_*`, sayılmadı).
- 📏 Testler hariç tüm `db/install.xml` dosyalarında 499 tablo tanımı var ("~500" diye söyle).
- ✅ Ders tamamlama hesabı cron'da: `lib/classes/task/completion_regular_task.php` (golden master README'si de bunu anlatıyor).

---

## Diyagram 2: Eklenti (plugin) nedir, bir eklentinin içi neye benzer?

```mermaid
flowchart TB
    subgraph CORE["Çekirdek · lib/enrollib.php"]
        direction LR
        FIND["enrol_get_plugin('self')<br/>sınıf adı: enrol_self_plugin<br/>dosya: enrol/self/lib.php"]
        BASE["abstract class enrol_plugin<br/>enrol_user() · unenrol_user()<br/>enrol_page_hook()"]
    end

    subgraph SELF["Eklenti klasörü · enrol/self/"]
        direction LR
        FILES["version.php · sürüm<br/>db/access.php · yetki enrol/self:enrolself<br/>db/install.php, db/upgrade.php · kurulum<br/>lang/en/enrol_self.php · ekran yazıları<br/>settings.php · yönetici ayarları<br/>classes/ · form, privacy, task"]
        LIB["lib.php<br/>class enrol_self_plugin<br/>extends enrol_plugin<br/>asıl mantık burada"]
    end

    FIND -->|"1 · isim kuralıyla dosyayı bulur"| LIB
    LIB -->|"2 · ana sınıftan miras alır"| BASE
```

Aynı türden kardeş eklentiler (✅ `enrol/` klasörü, 13 tane): `manual`, `self`, `guest`, `cohort`, `meta`, `category`, `database`, `ldap`, `lti`, `fee`, `paypal`, `flatfile`, `imsenterprise`.

**Bunu şöyle anlat:**
> "Moodle'ın gücü eklentiler. 'Derse kayıt' tek bir kod değil; 13 ayrı kayıt eklentisi var: öğretmenin elle eklemesi (`manual`), öğrencinin kendi kendine kaydolması (`self`), misafir, ödeme ile kayıt... Hepsi aynı ana sınıftan (`enrol_plugin`) türüyor. Moodle bir eklentiyi isminden buluyor: `self` diyorsa `enrol/self/lib.php` dosyasını açıp `enrol_self_plugin` sınıfını arıyor. Her eklentinin klasörü aynı düzende: sürüm dosyası, ana kod, yetkiler, ekran yazıları."
>
> Neden önemli: Bir kuralı 'kayıt sırasında' eklemek isterseniz, 13 eklentiye ayrı ayrı eklemeniz gerekir. Ön koşul özelliğinin (MDL-22648) 2012'den beri yapılamamasının sebebi bu.

**Kanıt:**
- ✅ `lib/enrollib.php:142` `enrol_get_plugin()`: `$class = "enrol_{$name}_plugin";` sonra `include_once("$location/lib.php")`.
- ✅ `lib/enrollib.php:1882` `abstract class enrol_plugin`; `enrol/self/lib.php:33` `class enrol_self_plugin extends enrol_plugin`.
- ✅ `enrol/self/db/access.php:77` `'enrol/self:enrolself'` yetkisi, `user` (giriş yapmış herkes) arketipine açık.
- ✅ 13 kayıt eklentisinin hiçbiri kayıttan önce başka bir dersin tamamlanmasına bakmıyor (`grep -il completion enrol/*/lib.php` 4 dosya buluyor; hepsi ders ayarı kopyalama ya da LTI not senkronu, ön koşul değil). Bkz. [advising SRS, REQ-ADV-08](../srs/modules/advising.md).

---

## Diyagram 3: Bir isteğin yolculuğu: "Öğrenci CS201 dersine kendi kendine kaydoluyor"

Senaryo: `student5` (Emre Doğan) CS101'i **tamamlamadı**. CS201'in tamamlama kuralında "önce CS101 tamamlanmış olmalı" yazıyor. Öğretmen CS201'e "Self enrolment" yöntemini eklemiş. Öğrenci "Enrol me" düğmesine basıyor.

```mermaid
sequenceDiagram
    autonumber
    actor S as student5
    participant IDX as enrol/index.php
    participant SETUP as config.php<br/>lib/setup.php
    participant SELF as enrol/self/<br/>lib.php
    participant CORE as lib/<br/>enrollib.php
    participant ACC as lib/<br/>accesslib.php
    participant DB as PostgreSQL<br/>(mdl_*)

    S->>IDX: "Enrol me" düğmesi<br/>GET enrol/index.php?id=CS201&action=enrol
    IDX->>SETUP: require('../config.php')
    SETUP-->>IDX: $DB, $USER, $PAGE, $OUTPUT hazır
    IDX->>DB: $DB->get_record('course', id)
    IDX->>IDX: enrol_get_plugins() + enrol_get_instances()<br/>derste açık kayıt yöntemlerini bul
    IDX->>SELF: enrol_page_hook($instance)
    SELF->>SELF: can_self_enrol()<br/>misafir mi? zaten kayıtlı mı? yöntem açık mı?<br/>tarih aralığı? kontenjan? yetki var mı?
    Note over SELF: ❗ "CS101 tamamlandı mı?" diye<br/>HİÇBİR yerde sorulmuyor
    SELF->>SELF: confirm_sesskey() → enrol_self()
    SELF->>CORE: enrol_user($instance, $USER->id, öğrenci rolü)
    CORE->>DB: INSERT mdl_user_enrolments
    CORE->>CORE: olay: user_enrolment_created<br/>kanca: after_user_enrolled
    CORE->>ACC: role_assign(öğrenci, kullanıcı, ders bağlamı)
    ACC->>DB: INSERT mdl_role_assignments
    IDX->>IDX: is_enrolled() → evet
    IDX-->>S: redirect course/view.php → öğrenci derste ✅
```

**Bunu şöyle anlat:**
> "Öğrenci 'Beni kaydet' düğmesine bastığında `enrol/index.php` çalışıyor. Bu dosya önce ortak ayarları yüklüyor, sonra derste hangi kayıt yöntemleri açık diye bakıyor ve her birine 'sen bu sayfada ne göstermek istiyorsun' diye soruyor. Kendi kendine kayıt eklentisi birkaç kontrol yapıyor: misafir mi, zaten kayıtlı mı, tarih uygun mu, kontenjan dolu mu, yetkisi var mı. Hepsi tamamsa çekirdekteki `enrol_user()` fonksiyonunu çağırıyor; o da veritabanına iki satır yazıyor: 'bu kişi bu derse kayıtlı' ve 'bu kişinin bu derste rolü öğrenci'. Dikkat: bu yolun hiçbir adımında 'önceki dersi geçti mi?' sorusu yok. Bu yüzden CS101'den kalan öğrenci CS201'e girebiliyor."

**Kanıt (hepsi ✅, Moodle 5.3.1):**

| Adım | Dosya:satır |
|---|---|
| 2 | `enrol/index.php:25` `require('../config.php')` |
| 4-6 | `enrol/index.php:73-82` `enrol_get_plugins(true)`, `enrol_get_instances()`, `enrol_page_hook($instance)` |
| 7 | `enrol/self/lib.php:275` `can_self_enrol()`, `:296` `has_capability('enrol/self:enrolself', ...)`, `:310` `is_self_enrol_available()` (durum, tarih, yeni kayda açık mı, kontenjan, kohort) |
| 8 (not) | `enrol/self/lib.php` ve `enrol/index.php` içinde "completion" kelimesi hiç geçmiyor (`grep -c` = 0); `enrol_user()` içinde de yok |
| 9 | `enrol/self/lib.php:235-236` `confirm_sesskey()` sonra `$this->enrol_self(...)`; `:189` `$this->enrol_user(...)` |
| 11 | `lib/enrollib.php:2148` `$DB->insert_record('user_enrolments', $ue)` |
| 12 | `lib/enrollib.php:2155` `user_enrolment_created::create`, `:2171` `after_user_enrolled` |
| 13-14 | `lib/enrollib.php:2181/2183` `role_assign(...)`; `lib/accesslib.php:1651` `insert_record('role_assignments', ...)` |
| 15-16 | `enrol/index.php:87-94` `is_enrolled(...)` sonra `redirect($destination)` |

- ⚠️ Şifreli (enrolment key) kayıtta düğme bir açılır pencere (modal) formu açar (`enrol/self/lib.php:222-230`); bu diyagram şifresiz yolu gösteriyor. Canlı demoda şifre koymayın.

---

## Diyagram 4: Seçtiğimiz 5 modül Moodle'ın neresinde ve birbirine nasıl bağlı?

Kutular: modülün Moodle'daki klasörleri. Oklar: "A'daki kaç PHP dosyası B'nin tablosuna ya da fonksiyonuna **doğrudan** dokunuyor" 📏.

```mermaid
flowchart LR
    IDN["🔐 Kimlik ve yetki<br/>auth/ · login/ · admin/roles/<br/>lib/accesslib.php<br/>188 dosya"]
    CRS["📚 Ders ve kayıt<br/>course/ · enrol/<br/>lib/enrollib.php<br/>567 dosya"]
    ADV["🧭 Danışmanlık karşılığı<br/>availability/ · completion/<br/>lib/completionlib.php<br/>73 dosya"]
    ASG["📝 Ödev<br/>mod/assign/<br/>290 dosya"]
    GRD["📊 Not<br/>grade/ · lib/grade/<br/>lib/gradelib.php<br/>320 dosya"]

    CRS -->|162| IDN
    GRD -->|115| IDN
    GRD -->|62| CRS
    CRS -->|42| ADV
    ASG -->|40| IDN
    CRS -->|24| GRD
    ADV -->|18| CRS
    ADV -->|14| IDN
    ASG -->|12| CRS
    IDN -->|9| CRS
    ASG -->|6| ADV
    ADV -->|5| GRD
    ASG -->|3| GRD
    GRD -->|1| ADV
```

Tam tablo ve sayım yöntemi: [moodle-coupling/results.md](moodle-coupling/results.md), script: [moodle-coupling/measure_coupling.py](moodle-coupling/measure_coupling.py).

**Bunu şöyle anlat:**
> "Seçtiğimiz beş modül Moodle'da ayrı klasörlerde duruyor ama gerçekte birbirine sıkı sıkıya bağlı. Okların üstündeki sayı, bir modülün kaç dosyasının diğer modülün tablosuna ya da fonksiyonuna doğrudan dokunduğu. Örneğin ders ve kayıt modülünün 162 dosyası yetki koduna, not modülünün 62 dosyası ders tablolarına doğrudan erişiyor. Oklar iki yönlü de var: ders → danışmanlık ve danışmanlık → ders. Yani birini değiştirdiğinizde diğerleri de etkilenebilir; parçaları tek tek söküp yenisini takmak zor."
>
> Danışmanlık modülü Moodle'da yok; en yakın karşılığı "erişim kısıtlamaları" (`availability/`) ve "ders tamamlama" (`completion/`).

**Yöntem ve sınırları (📏):**
- Script Moodle'ın PHP dosyalarını düzenli ifadelerle (regex) tarıyor, PHP'yi gerçekten çözümlemiyor. Sayılar **yaklaşık**; şekli (kim kime bağlı, kabaca ne kadar) göstermek için kullanın, kesin değer gibi sunmayın.
- Bir dosya, B'nin bir tablosunun adını (`$DB->...('user_enrolments')` ya da SQL içinde `{user_enrolments}`) geçiriyorsa ya da B'nin bilinen bir fonksiyonunu çağırıyorsa (`has_capability`, `is_enrolled`, `grade_update`, `new completion_info` ...) sayılır. Her (A, B) çifti için bir dosya bir kez sayılır. Test dosyaları hariç.
- Hangi tablonun hangi modüle ait olduğu: eklenti tabloları kendi `db/install.xml` dosyasından, çekirdek tablolar elle atandı (listesi script'in başında).
- Ödev → not oku küçük çıkıyor (3) çünkü `mod/assign/locallib.php` tek başına çok büyük bir dosya; dosya sayıyoruz, çağrı sayısı değil.
- Yeniden üretmek için: `python3 docs/architecture/moodle-coupling/measure_coupling.py <moodle>/public`
