/**
 * Tahvil API gövdesinde yapılandırılmış yield alanları (ör. YTM) ile
 * `yieldPct` / fiyat serisini ayırır. Fiyat performansı UI'si `yieldPct`'i
 * “tahvil faizi” olarak sunmaz; vade–getiri eğrisi yalnızca bu alanlardan biri
 * anlamlı geldiğinde gösterilir.
 */
export type DebtYieldCarrier = {
    yieldPct?: number;
    yieldToMaturity?: number | null;
    simpleYield?: number | null;
    compoundYield?: number | null;
};

function finiteOrUndef(v: unknown): number | undefined {
    if (v == null) return undefined;
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : undefined;
}

/** API'den gelen YTM / basit / bileşik getiri alanlarından ilki. */
export function pickStructuredYieldDecimal(s: DebtYieldCarrier | null | undefined): number | undefined {
    if (!s) return undefined;
    return (
        finiteOrUndef(s.yieldToMaturity) ??
        finiteOrUndef(s.simpleYield) ??
        finiteOrUndef(s.compoundYield)
    );
}

export function debtHasStructuredYieldData(s: DebtYieldCarrier | null | undefined): boolean {
    return pickStructuredYieldDecimal(s) != null;
}
