# NrsFinancePortal

Derleme çıktısı commitlenmez.

## Üretim / v22 şema değişiklikleri

Eski finans tablolarını veya büyük Liquibase migrasyonlarını uygulamadan önce **tam veritabanı yedeği** alın (dump veya anlık görüntü + geri yükleme prosedürü test edilmiş olsun). Yedek olmadan şema düşürme veya veri silen changelog çalıştırmayın; geri dönüş için yedeği ayrı ortamda doğrulayın.
