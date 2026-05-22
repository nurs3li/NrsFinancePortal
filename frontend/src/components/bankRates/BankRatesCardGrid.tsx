import { TrendingDown, TrendingUp } from 'lucide-react';
import type { EnrichedBankRow } from '../../utils/bankRatesVm';
import { BankLogo } from './BankLogo';

type Props = {
    rows: EnrichedBankRow[];
    currency: string;
    locale: string;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
    t: (key: string, fallback?: string) => string;
};

function fmt(n: number, locale: string) {
    return n.toLocaleString(locale, { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

export function BankRatesCardGrid({ rows, currency, locale, tokens, t }: Props) {
    return (
        <div className="br-card-grid">
            {rows.map((row) => (
                <article
                    key={row.bankCode}
                    className="br-bank-card"
                    style={{ background: tokens.bgCard, borderColor: tokens.border }}
                >
                    <div className="br-bank-card__head">
                        <BankLogo bankName={row.bankName} size="md" />
                        <div>
                            <h3 className="br-bank-card__name">{row.bankName}</h3>
                            <span className="br-bank-card__code" style={{ color: tokens.textMuted }}>
                                {row.bankCode}
                            </span>
                        </div>
                        {row.trend === 'UP' ? (
                            <TrendingUp size={18} className="br-trend--up" />
                        ) : row.trend === 'DOWN' ? (
                            <TrendingDown size={18} className="br-trend--down" />
                        ) : null}
                    </div>
                    <div className="br-bank-card__rates">
                        <div>
                            <span style={{ color: tokens.textMuted }}>
                                {currency} {t('bankRates.colBuy', 'Alış')}
                            </span>
                            <strong>{fmt(row.buy, locale)} ₺</strong>
                        </div>
                        <div>
                            <span style={{ color: tokens.textMuted }}>
                                {currency} {t('bankRates.colSell', 'Satış')}
                            </span>
                            <strong>{fmt(row.sell, locale)} ₺</strong>
                        </div>
                    </div>
                    <div className="br-bank-card__spread">
                        <span>{t('bankRates.spreadRange', 'Makas aralığı')}</span>
                        <strong>
                            {fmt(row.spread, locale)} ₺ ({row.spreadPct.toFixed(2)}%)
                        </strong>
                    </div>
                </article>
            ))}
        </div>
    );
}
