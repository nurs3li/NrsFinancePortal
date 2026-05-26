# README görselleri ve demo GIF'leri

GitHub `README.md` dosyasında kullanılacak ekran görüntüleri, mimari diyagramları ve animasyonlu GIF'ler bu klasöre konur.

---

## Önerilen dosyalar

| Dosya adı | İçerik | README bölümü |
|-----------|--------|---------------|
| `architecture.png` | Excalidraw / draw.io mimari diyagramı | Sistem mimarisi |
| `project-structure.gif` | VS Code / Explorer klasör ağacı turu | Proje yapısı |
| `docker-compose-up.gif` | `docker compose up` + `docker compose ps` | Kurulum |
| `portal-demo.gif` | Login → dashboard → piyasa kısa demo | Genel tanıtım |
| `swagger-ui.png` | Swagger UI ekran görüntüsü | API dokümantasyonu |
| `grafana-audit.png` | Admin audit Grafana panelleri | Observability |

---

## Markdown kullanımı

Repo kökünden referans:

```markdown
![Proje mimarisi](./docs/assets/architecture.png)
*Şekil 1 — Bileşenler arası veri ve log akışı*

![Klasör yapısı](./docs/assets/project-structure.gif)
*Şekil 2 — Proje dizin ağacı*
```

Alt metin (italik satır) GitHub'da görselin altında caption olarak görünür.

---

## Windows'ta GIF kaydı

### ScreenToGif (önerilen)

1. https://www.screentogif.com/ indirin
2. **Recorder** → pencere veya bölge seçin
3. 10–25 saniye kayıt (klasör açma, terminal komutu, portal gezintisi)
4. **Save as** → bu klasöre kaydedin

**Optimizasyon:**

- Genişlik: ~1200px
- FPS: 10–15
- Süre: kısa tutun (repo boyutu)

### OBS Studio alternatifi

1. OBS ile MP4 kaydet
2. https://ezgif.com/video-to-gif adresinde GIF'e dönüştür
3. Sonucu `docs/assets/` altına koy

---

## Statik diyagram (PNG)

Örnek projelerdeki el çizimi stil için:

1. https://excalidraw.com/ açın
2. Bileşenleri çizin (Frontend, Keycloak, servisler, DB, Kafka)
3. **Export PNG** → `architecture.png`

Alternatif: https://app.diagrams.net/

> Kök README şu an **Mermaid** diyagramı kullanıyor — GitHub otomatik render eder. PNG isteğe bağlıdır.

---

## Güvenlik

GIF/PNG kaydında **göstermeyin:**

- Gerçek API key'ler (`.env` içeriği)
- Production şifreleri
- Kişisel e-posta / telefon

Demo hesapları veya blur kullanın.

---

## Commit sonrası

GitHub README otomatik güncellenir. Ek CDN gerekmez. Büyük GIF'ler (>5 MB) clone süresini uzatır — mümkünse sıkıştırın.

---

[← Dokümantasyon hub](../README.md)
