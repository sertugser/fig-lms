# 2. Yeni sistem: Fig LMS nasıl çalışıyor?

Kaynak: bu repodaki `backend/` (25 Kasım 2026 itibarıyla `main`), [ADR-0001](../adr/0001-language-and-architecture.md). Cevap örnekleri backend gerçekten çalıştırılarak alındı (PostgreSQL + `SAMPLE_USER_PASSWORD=demo`).

İşaretler: ✅ çalışıyor, kodda var · 🟡 geçici çözüm · ⬜ sadece iskelet (boş paket) · 🔜 planlandı, henüz kod yok

---

## Diyagram 5: Yeni sistem büyük resim

```mermaid
flowchart TB
    U["👤 İstemci<br/>bugün: curl ve testler · 🔜 React arayüz"]
    SEC["🟡 Güvenlik kapısı · Spring Security<br/>identity/internal/SecurityConfig · giriş yoksa 401"]

    subgraph ADV["✅ advising modülü"]
        direction TB
        AINT["internal/ · mutfak<br/>PrerequisiteController<br/>DefaultPrerequisiteService<br/>AdvisingRepository"]
        AAPI["api/ · vitrin<br/>PrerequisiteService<br/>PrerequisiteCheck"]
    end

    subgraph REST["⬜ diğer modüller: klasörler hazır, içi boş"]
        direction LR
        CRS["course"]
        ASG["assignment"]
        GRD["grading"]
        PLN["planning · yeni"]
    end

    DB[("PostgreSQL · şema Flyway ile sürümlü<br/>adv_course · adv_student<br/>adv_prerequisite · adv_course_completion")]
    CI{{"ModularityTest · her PR'da CI<br/>başka modülün internal'ına dokunan kod = kırmızı build"}}

    U -->|"HTTP · tek Spring Boot uygulaması, Java 21"| SEC
    SEC --> AINT
    AINT -->|"interface'i uygular"| AAPI
    AINT -->|SQL| DB
    CRS -.->|"🔜 kayıttan önce sor"| AAPI
    PLN -.->|"🔜 plan yaparken sor"| AAPI
    CI -.->|denetler| ADV
    CI -.->|denetler| REST
```

**Bunu şöyle anlat:**
> "Yeni sistem de tek bir program, yani hâlâ monolit; ama içi odalara bölünmüş. Altı modülümüz var: kimlik, ders, danışmanlık, ödev, not ve yeni eğitim planı. Her modülün iki bölümü var: `api` vitrin, `internal` mutfak. Başka bir modül sadece vitrinden alışveriş yapabilir, mutfağa giremez. Bu sadece bir söz değil: her PR'da çalışan `ModularityTest` testi mutfağa giren kodu bulursa build kırmızı oluyor ve kod birleşemiyor."
>
> "Bugün tam çalışan modülümüz danışmanlık: ön koşul kontrolü. Diğer modüllerin klasörleri hazır ama içleri boş. Ok kesikli çizilenler plan: ders modülü kayıttan önce danışmanlığa 'bu öğrenci girebilir mi?' diye soracak."
>
> Benzetme: Moodle'daki "herkesin her odaya girdiği ev" yerine, her odanın tek bir kapısı (api) olduğu ve kapı dışından girmeye çalışanı alarmın (CI) yakaladığı bir ev.

**Kanıt:**
- ✅ Modüller: `backend/src/main/java/com/figlms/{identity,course,advising,assignment,grading,planning}`, her birinde `api/` ve `internal/`. `ModularityTest.hasTheSixModulesFromAdr0001()` altısını kontrol ediyor.
- ✅ `advising/api/package-info.java`: `@NamedInterface("api")`. Diğer modüllerin dokunabileceği tek paket.
- ✅ `ModularityTest.verifiesModuleBoundaries()`: `ApplicationModules.of(FigLmsApplication.class).verify()`. CI: `.github/workflows/ci.yml` içinde `./mvnw -B verify`.
- ✅ Şema: `backend/src/main/resources/db/migration/V1__advising_prerequisites.sql`, `V2__advising_sample_data.sql` (Moodle'daki örnek veriyle aynı: CS201 için CS101 gerekli; student1 ve student2 CS101'i tamamladı).
- 🟡 Giriş şimdilik HTTP Basic ve sabit örnek kullanıcılar (`SecurityConfig`). Kimlik modülü gerçek hesapları ve token'ı sonra getirecek.
- 🔜 course → advising ve planning → advising okları **henüz kodda yok**. Şu an modüller arasında hiç bağımlılık yok (`grep "^import com.figlms" backend/src/main` sadece advising'in kendi içini gösteriyor).
- ⚠️ Dürüst not: `ModularityTest` Java bağımlılıklarını denetler. Bir modülün SQL ile başka modülün tablosunu okumasını **otomatik yakalamaz**. Bunu şimdilik tablo önekiyle (`adv_`) ve kod incelemesiyle koruyoruz.

---

## Diyagram 6: Aynı soru yeni sistemde: "student5 CS201'e girebilir mi?"

```mermaid
sequenceDiagram
    autonumber
    actor S as student5
    participant SEC as Spring Security<br/>(identity)
    participant C as Prerequisite<br/>Controller
    participant API as Prerequisite<br/>Service (api)
    participant SVC as Default<br/>PrerequisiteService
    participant R as Advising<br/>Repository
    participant DB as PostgreSQL (adv_*)

    S->>SEC: GET prerequisite-check<br/>student=student5, course=CS201
    alt giriş yok / şifre yanlış
        SEC-->>S: 401
    end
    SEC->>C: check(student5, CS201, giriş yapan kişi)
    alt öğrenci başkasını soruyor
        C-->>S: 403
    end
    C->>API: check("student5", "CS201")
    API->>SVC: Spring arkadaki sınıfı bağlar
    SVC->>R: studentExists? courseExists?
    R->>DB: SELECT COUNT(*) ...
    alt öğrenci/ders yok
        SVC-->>S: 404 UNKNOWN_STUDENT / UNKNOWN_COURSE
    end
    SVC->>R: prerequisitesOf("CS201")
    R->>DB: SELECT prerequisite FROM adv_prerequisite
    R-->>SVC: [CS101]
    SVC->>R: completedCourses("student5")
    R->>DB: SELECT course FROM adv_course_completion
    R-->>SVC: { } (hiçbiri)
    SVC-->>C: allowed=false, eksik: [CS101]
    C-->>S: 200 · allowed=false · eksik [CS101]
```

Gerçek cevaplar (backend çalıştırılarak alındı, şifre `demo`):

```text
$ curl -u student5:demo "http://localhost:8080/api/advising/prerequisite-check?student=student5&course=CS201"
{"student":"student5","course":"CS201","allowed":false,"reason":"PREREQUISITES_MISSING","missingPrerequisites":["CS101"]}

$ curl -u student1:demo "http://localhost:8080/api/advising/prerequisite-check?student=student1&course=CS201"
{"student":"student1","course":"CS201","allowed":true,"reason":"PREREQUISITES_COMPLETED","missingPrerequisites":[]}

$ curl -u student1:demo "http://localhost:8080/api/advising/prerequisite-check?student=student1&course=MATH101"
{"student":"student1","course":"MATH101","allowed":true,"reason":"NO_PREREQUISITES","missingPrerequisites":[]}

$ curl -u student3:demo "...?student=student1&course=CS201"     # başkasını soruyor
HTTP 403

$ curl -u x:y "...?student=student1&course=CS201"               # yanlış giriş
HTTP 401

$ curl -u teacher1:demo "...?student=student9&course=CS201"     # olmayan öğrenci
{"message":"Unknown student: student9","error":"UNKNOWN_STUDENT"}   HTTP 404
```

**Bunu şöyle anlat:**
> "İstek önce güvenlik kapısından geçiyor: giriş yoksa 401, kapı açılmıyor. Sonra 'controller' denen karşılama masası soruyu alıyor: öğrenci başkasının durumunu soruyorsa 403. Sonra soru danışmanlık modülünün vitrinindeki `PrerequisiteService`'e gidiyor. Asıl işi arkadaki `DefaultPrerequisiteService` yapıyor: veritabanından 'CS201'in ön koşulları ne?' diye soruyor, cevap CS101; 'student5 hangi dersleri bitirdi?' diye soruyor, cevap hiçbiri. Sonuç: giremez, eksik CS101. Moodle'da aynı öğrenci düğmeye basınca derse giriyordu."
>
> "Kural tek bir yerde, tek bir dosyada: `DefaultPrerequisiteService`. Kayıt nasıl yapılırsa yapılsın (öğrenci kendisi, öğretmen, ileride toplu kayıt) hepsi bu tek yere soracak. Moodle'da kural 13 kayıt eklentisine dağılmak zorunda olduğu için yapılamamıştı."

**Kanıt:** ✅ `advising/internal/PrerequisiteController.java` (401/403/400/404 kuralları sınıf yorumunda), `advising/internal/DefaultPrerequisiteService.java`, `advising/internal/AdvisingRepository.java`. Testler: `PrerequisiteControllerTest`, `PrerequisiteServiceTest`.

- ⚠️ Not: 403 cevabının gövdesi Spring'in varsayılan hata biçiminde (`{"timestamp":...,"status":403,"error":"Forbidden",...}`), 404/400 ise bizim biçimimizde (`{"error":...,"message":...}`). Tutarsızlık; ileride düzeltilmeli.

---

## Diyagram 7: Kuralı bozmaya çalışırsak ne olur? (canlı gösterilebilir)

```mermaid
flowchart TB
    X["Biri course modülüne yazıyor:<br/>import com.figlms.advising.internal.AdvisingRepository;"]
    J{"Java derleyicisi"}
    M{"ModularityTest<br/>(Spring Modulith)"}
    F1["❌ DERLENMEZ<br/>AdvisingRepository is not public ...<br/>cannot be accessed from outside package"]
    F2["❌ TEST KIRMIZI<br/>Module 'course' depends on non-exposed type<br/>com.figlms.advising.internal.AdvisingTables<br/>within module 'advising'!"]
    OK["✅ Doğru yol: advising.api.PrerequisiteService"]

    X --> J
    J -->|"sınıf package-private"| F1
    J -->|"sınıfı biri public yapmışsa"| M
    M --> F2
    X -.->|"yerine"| OK
```

**Bunu şöyle anlat:**
> "Diyelim ki bir arkadaşımız ya da yapay zekâ kestirme yol denedi: ders modülünden danışmanlığın mutfağındaki veritabanı sınıfını doğrudan kullanmak istedi. İki kat koruma var. Birincisi Java derleyicisi: o sınıf dışarıya kapalı, kod derlenmiyor bile. İkincisi, biri o sınıfı yanlışlıkla açık (`public`) yapsa bile `ModularityTest` 'course modülü advising'in gizli bir sınıfına bağımlı' diye hata veriyor. Moodle'da bunun karşılığı yok: herhangi bir dosya herhangi bir tabloya `$DB` ile yazabiliyor."

**Kanıt:** ✅ İki deneme de 25 Kasım'da bu repoda çalıştırıldı, hata mesajları yukarıdaki gibi birebir. Canlı gösterim adımları: [04-canli-demo.md](04-canli-demo.md#adım-5).
