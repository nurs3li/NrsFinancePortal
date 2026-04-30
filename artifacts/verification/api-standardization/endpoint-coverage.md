# Endpoint Coverage

- Scope: /api/** endpoints in finance-service + marketdata
- Strategy: ResponseBodyAdvice based automatic envelope wrapping + explicit exception envelope
- Total /api endpoints scanned: 44
- Envelope compliant endpoints: 43
- Intentional exception (binary/stream): 1 (/api/fund-requests/receipts/{receiptId})
- Coverage including exceptions: 97.7%
- Coverage excluding intentional exceptions: 100%

## Notes
- /internal/** and /health/** are out of scope by policy.
- Legacy error field is preserved additively inside errors for backward compatibility.
