# Mimari: eski (Moodle) ve yeni (Fig LMS) yan yana

2. hafta sunumu için hazırlandı. Hocanın isteği: Moodle'ın mimarisini diyagramlarla, eskiyi ve yeniyi yan yana, canlı göstererek ve sade dille anlatmak.

| Dosya | İçinde ne var |
|---|---|
| [01-moodle.md](01-moodle.md) | Diyagram 1-4: Moodle büyük resim, eklenti yapısı, "öğrenci derse kaydoluyor" isteğinin dosya dosya yolu, 5 modülün Moodle'daki yeri ve bağımlılıkları |
| [02-yeni-sistem.md](02-yeni-sistem.md) | Diyagram 5-7: Fig LMS büyük resim, aynı sorunun yeni sistemdeki yolu (gerçek curl cevaplarıyla), modül sınırını bozmayı deneyince ne olduğu |
| [03-karsilastirma.md](03-karsilastirma.md) | Diyagram 8-10: aynı soru iki sistemde, karşılaştırma tablosu, bağımlılık haritası, golden master |
| [04-canli-demo.md](04-canli-demo.md) | Sunumda adım adım canlı gösterim: Moodle'da kayıt, yeni sistemde aynı soru, kuralı bozma, golden master |
| [moodle-coupling/](moodle-coupling/) | Diyagram 4'teki sayıları üreten script ve sonuçları |

Her diyagramın altında **"Bunu şöyle anlat"** notu var: konuyu hiç bilmeyen birine anlatır gibi yazıldı. Sunumdan önce ekipteki herkes bu notları kendi cümleleriyle anlatabilmeli.

## Görsel sayfa
[`docs/presentation/2026-12-week2-mimari/index.html`](../presentation/2026-12-week2-mimari/index.html): bütün diyagramlar tek sayfada, internetsiz açılır (dosyaya çift tıklayın). GitHub'da .md dosyalarındaki diyagramlar da doğrudan çizilir.

Diyagramı değiştirmek için: .md dosyasındaki `mermaid` bloğunu düzenleyin, sonra sayfayı yeniden üretin:
```bash
npm install --prefix /tmp/mermaid mermaid@11.4.1 playwright
npx --prefix /tmp/mermaid playwright install chromium
NODE_PATH=/tmp/mermaid/node_modules python3 docs/architecture/build_page.py \
  /tmp/mermaid/node_modules/mermaid/dist/mermaid.min.js docs/presentation/2026-12-week2-mimari/index.html
```

## Güvenilirlik işaretleri
- ✅ kaynak koddan doğrulandı (Moodle 5.3.1, `v5.3.1` etiketi; yollar `public/` klasörüne göre) ya da bu repoda çalıştırıldı
- 📏 kendi script'imizle sayıldı; yöntem ve sınırları dosyada yazıyor
- 🟡 geçici çözüm · ⬜ sadece iskelet · 🔜 planlandı, henüz kod yok
- ⚠️ doğrulanmadı; sunumdan önce provada kontrol edin
