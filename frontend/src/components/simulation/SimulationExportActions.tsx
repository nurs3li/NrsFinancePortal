import { useLanguage } from '../../i18n/LanguageContext';

type SimulationExportActionsProps = {
    onExportCsv: () => void;
    onExportPdf: () => void;
    onSaveSimulation: () => void;
    disabled: boolean;
    saveDisabled?: boolean;
};

export function SimulationExportActions({ onExportCsv, onExportPdf, onSaveSimulation, disabled, saveDisabled }: SimulationExportActionsProps) {
    const { t } = useLanguage();

    return (
        <div className="sim-export-block">
            <h3 className="sim-export-block__title">{t('simulation.reportingTitle', 'Raporlama')}</h3>
            <p className="sim-lead sim-export-block__hint">
                {t('simulation.reportingHint', 'Simülasyon sonuçlarını sunum veya arşiv için dışa aktar.')}
            </p>
            <div className="sim-export-block__actions">
                <button type="button" className="sim-toolbar-btn sim-csv-btn" disabled={disabled} onClick={onExportPdf}>
                    {t('simulation.exportPdf', 'PDF Rapor Oluştur')}
                </button>
                <button type="button" className="sim-toolbar-btn sim-csv-btn" disabled={disabled} onClick={onExportCsv}>
                    {t('simulation.exportCsv', 'CSV Verisini İndir')}
                </button>
                <button type="button" className="sim-toolbar-btn sim-save-btn" disabled={saveDisabled ?? disabled} onClick={onSaveSimulation}>
                    {t('simulation.saveSimulation', 'Simülasyonu Kaydet')}
                </button>
            </div>
        </div>
    );
}
