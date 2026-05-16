/** Takvim günü Europe/Istanbul (backend ile uyumlu). */
export function istanbulTodayYmd(): string {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Istanbul' }).format(new Date());
}

/** Varsayılan alım tarihi: dün (İstanbul). */
export function defaultSimulationBuyDate(): string {
    const [y, m, d] = istanbulTodayYmd().split('-').map(Number);
    const utc = new Date(Date.UTC(y, m - 1, d));
    utc.setUTCDate(utc.getUTCDate() - 1);
    return utc.toISOString().slice(0, 10);
}
