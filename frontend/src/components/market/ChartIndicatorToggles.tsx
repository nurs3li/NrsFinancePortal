import { useLanguage } from '../../i18n/LanguageContext';

type Props = {
    showMa: boolean;
    showRsi: boolean;
    onToggleMa: () => void;
    onToggleRsi: () => void;
    /** VIOP / tahvil gibi modlarda RSI paneli yok */
    rsiAvailable?: boolean;
};

export function ChartIndicatorToggles({ showMa, showRsi, onToggleMa, onToggleRsi, rsiAvailable = true }: Props) {
    const { t } = useLanguage();

    return (
        <div className="terminal-btn-row terminal-btn-row--indicators" role="group" aria-label={t('market.chartIndicators', 'Grafik göstergeleri')}>
            <button
                type="button"
                className="terminal-btn terminal-btn--indicator"
                aria-pressed={showMa}
                title={
                    showMa
                        ? t('market.indicatorMaOff', 'Hareketli ortalamayı kapat')
                        : t('market.indicatorMaOn', 'Hareketli ortalamayı aç')
                }
                onClick={(e) => {
                    e.stopPropagation();
                    onToggleMa();
                }}
            >
                MA
            </button>
            {rsiAvailable ? (
                <button
                    type="button"
                    className="terminal-btn terminal-btn--indicator"
                    aria-pressed={showRsi}
                    title={
                        showRsi
                            ? t('market.indicatorRsiOff', 'RSI panelini kapat')
                            : t('market.indicatorRsiOn', 'RSI panelini aç')
                    }
                    onClick={(e) => {
                        e.stopPropagation();
                        onToggleRsi();
                    }}
                >
                    RSI
                </button>
            ) : null}
        </div>
    );
}
