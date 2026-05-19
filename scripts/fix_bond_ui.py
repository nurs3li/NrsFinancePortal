from pathlib import Path

p = Path(r"c:\Users\Nurseli\Desktop\NrsFinancePortal\frontend\src\pages\Market.tsx")
c = p.read_text(encoding="utf-8")

d_close = "</" + "div>"

# Remove YTM / yield pending block in drawer
start = c.find("                                            {selectedInstrumentVm.yieldToMaturity != null &&")
if start == -1:
    raise SystemExit("yield block start not found")
end = c.find("                                            )}", start)
if end == -1:
    raise SystemExit("yield block end not found")
end += len("                                            )}")
c = c[:start] + c[end:]

# Fix coupon value line
old_coupon = (
    "                                            <div>%{Number(selectedInstrumentVm.couponRate ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}"
    + d_close
)
new_coupon = (
    "                                            <motion.div>\n"
    "                                                {formatBondCouponRate(\n"
    "                                                    selectedInstrumentVm.couponRate,\n"
    "                                                    lang === 'en' ? 'en-US' : 'tr-TR',\n"
    "                                                )}\n"
    "                                            " + d_close
)
new_coupon = new_coupon.replace("<motion.div>", "<" + "motion.div>").replace(d_close, d_close)
new_coupon = (
    "                                            <div>\n"
    "                                                {formatBondCouponRate(\n"
    "                                                    selectedInstrumentVm.couponRate,\n"
    "                                                    lang === 'en' ? 'en-US' : 'tr-TR',\n"
    "                                                )}\n"
    "                                            " + d_close
)
if old_coupon not in c:
    raise SystemExit("coupon line not found")
c = c.replace(old_coupon, new_coupon, 1)

# Add title to Kupon Oranı label row
old_label = "                                            <div>Kupon Oranı" + d_close
new_label = (
    "                                            <div\n"
    "                                                title={t(\n"
    "                                                    'market.bondCouponTip',\n"
    "                                                    'Kupon oranı, tahvilin nominal değer üzerinden yaptığı faiz ödemesidir; vadeye kadar getiri değildir.',\n"
    "                                                )}\n"
    "                                            >\n"
    "                                                {t('market.coupon', 'Kupon Oranı')}\n"
    "                                            " + d_close
)
if old_label in c:
    c = c.replace(old_label, new_label, 1)

# Mini card: drop yield row
yield_row_start = c.find("                                            <dt>{t('market.yield', 'Getiri')}</dt>")
if yield_row_start != -1:
    block_start = c.rfind("                                        <div>", 0, yield_row_start)
    block_end = c.find("                                        " + d_close, yield_row_start)
    block_end = c.find("\n", block_end) + 1
    c = c[:block_start] + c[block_end:]
    c = c.replace(
        "market.bondYieldSummaryTitle', 'Getiri bilgisi'",
        "market.bondPriceSummaryTitle', 'Piyasa değeri'",
        1,
    )

# bondDualPoints: don't pass coupon as yield for chart
c = c.replace(
    "                yieldPct: Number(x.yieldPct ?? 0),",
    "                yieldPct: 0,",
    1,
)

p.write_text(c, encoding="utf-8")
print("patched ok")
