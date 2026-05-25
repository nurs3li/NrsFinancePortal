# Banka logoları

`frontend/public/bank-logos/` — Banka Kurları sayfasında kullanılan PNG logolar.

## Dosya adlandırma

Dosya adı, banka kodunun **küçük harf** hali olmalıdır:

| Banka | Dosya |
|-------|-------|
| Ziraat | `ziraat.png` |
| İş Bankası | `isbank.png` |
| Garanti | `garanti.png` |
| Akbank | `akbank.png` |
| Yapı Kredi | `yapikredi.png` |

## Kullanım

`BankLogo.tsx` bileşeni banka koduna göre `/bank-logos/{code}.png` yolunu çözer.

Logo yoksa fallback metin gösterilir.

## Yeni logo ekleme

1. Şeffaf veya beyaz arka planlı PNG (tercihen ~128×128)
2. `frontend/public/bank-logos/{bankCode}.png` olarak kaydedin
3. `bankRatesVm` veya ilgili mapping'de banka kodunun eşleştiğini doğrulayın
