-- İlk Postgres cluster init'te (boş postgres_data volume) marketdata için nrs_market oluşturur.
-- Mevcut volume'da bu script tekrar çalışmaz; o durumda elle:
--   docker exec -it nrs-postgres psql -U nrs -d nrs_finance -c "CREATE DATABASE nrs_market;"
SELECT 'CREATE DATABASE nrs_market'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nrs_market')\gexec
