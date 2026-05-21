import re
import requests
from datetime import date

headers = {"User-Agent": "Mozilla/5.0", "Accept-Language": "tr-TR,tr;q=0.9"}
r = requests.get(
    "https://www.ziraatbank.com.tr/tr/bireysel/yatirim/eurobond",
    headers=headers,
    timeout=20,
)
print("status", r.status_code, "len", len(r.text))
for pat in [r"eurobond[^\"']{0,80}", r"Handlers/[^\"']+", r"api[^\"']*bond[^\"']*"]:
    hits = re.findall(pat, r.text, re.I)
    for h in hits[:8]:
        print("hit", h[:100])
