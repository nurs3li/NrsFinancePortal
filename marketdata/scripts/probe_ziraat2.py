import re
import requests

headers = {"User-Agent": "Mozilla/5.0"}
r = requests.get(
    "https://www.ziraatbank.com.tr/tr/bireysel/yatirim/eurobond",
    headers=headers,
    timeout=20,
)
text = r.text
for kw in ["ashx", "asmx", "api", "Handler", "Service", "ajax", "Eurobond", "Listele", "GetEuro"]:
    idx = 0
    while True:
        i = text.lower().find(kw.lower(), idx)
        if i < 0:
            break
        snippet = text[max(0, i - 40) : i + 80].replace("\n", " ")
        if "eurobond" in snippet.lower() or kw.lower() in ["ashx", "asmx", "geteuro"]:
            print(snippet[:120])
        idx = i + 1
