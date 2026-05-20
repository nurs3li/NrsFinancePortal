import { ArrowDown, ArrowUp } from 'lucide-react';
import { useInfoTerm } from '../education/InfoTermProvider';
import { ChartCard } from '../primitives/ChartCard';
import { EmptyStateCard } from '../primitives/EmptyStateCard';
import { MacroSection } from '../primitives/MacroSection';
import type { MacroTheme } from '../MacroTheme';

type Props = {
    tokens: MacroTheme;
};

export function MacroBondsSection({ tokens }: Props) {
    const { openTerm } = useInfoTerm();

    return (
        <MacroSection
            id="macro-bonds"
            title="Tahvil & Bono"
            summary="Tahvil ve bono, borçlanmak için çıkarılan sabit getirili araçlardır."
            termId="bond"
            infoAriaLabel="Tahvil ve bono hakkında bilgi"
            tokens={tokens}
        >
            <div className="macro-bond-edu">
                <button type="button" className="macro-term-chip" onClick={() => openTerm('bond')}>
                    Tahvil
                </button>
                <button type="button" className="macro-term-chip" onClick={() => openTerm('bono')}>
                    Bono
                </button>
                <button type="button" className="macro-term-chip" onClick={() => openTerm('coupon')}>
                    Kupon
                </button>
                <button type="button" className="macro-term-chip" onClick={() => openTerm('maturity')}>
                    Vade
                </button>
                <button type="button" className="macro-term-chip" onClick={() => openTerm('yieldCurve')}>
                    Getiri eğrisi
                </button>
                <button type="button" className="macro-term-chip" onClick={() => openTerm('bondPriceYield')}>
                    Faiz–fiyat ilişkisi
                </button>
            </div>

            <div className="macro-grid macro-grid--2">
                <ChartCard
                    title="Getiri eğrisi (yakında)"
                    termId="yieldCurve"
                    infoAriaLabel="Getiri eğrisi hakkında bilgi"
                    empty
                    emptyTitle="Canlı tahvil getiri eğrisi verisi hazırlanıyor"
                    emptyHint="Bu alan ileride piyasa verisiyle doldurulacak; şimdilik kavramsal öğrenme kartları aktif."
                    tokens={tokens}
                    height={200}
                >
                    {null}
                </ChartCard>

                <article className="macro-inverse-card" style={{ borderColor: tokens.border, background: tokens.bgCard }}>
                    <h3 className="macro-inverse-card__title" style={{ color: tokens.text }}>
                        Faiz ↑ ⇒ Tahvil fiyatı ↓
                    </h3>
                    <div className="macro-inverse-card__visual">
                        <div className="macro-inverse-card__side macro-inverse-card__side--up">
                            <ArrowUp size={28} aria-hidden />
                            <span>Faiz</span>
                        </div>
                        <div className="macro-inverse-card__side macro-inverse-card__side--down">
                            <ArrowDown size={28} aria-hidden />
                            <span>Eski tahvil fiyatı</span>
                        </div>
                    </div>
                    <button type="button" className="macro-link-btn" onClick={() => openTerm('bondPriceYield')}>
                        Neden böyle?
                    </button>
                </article>
            </div>

            <EmptyStateCard
                title="Tahvil piyasası verisi sınırlı"
                hint="DİBS ve bono fiyatları VİOP & Tahvil terminalinde; bu panel makro okuryazarlık odaklıdır."
                tokens={tokens}
            />
        </MacroSection>
    );
}
