type TabId = 'viop' | 'bond';

type Props = {
    active: TabId;
    onChange: (tab: TabId) => void;
    viopLabel: string;
    bondLabel: string;
};

export function ViopBondTabs({ active, onChange, viopLabel, bondLabel }: Props) {
    return (
        <div className="pf-range-tabs" role="tablist" style={{ marginBottom: '0.75rem' }}>
            <button
                type="button"
                role="tab"
                className={active === 'viop' ? 'pf-range-tabs__btn--on' : ''}
                onClick={() => onChange('viop')}
            >
                {viopLabel}
            </button>
            <button
                type="button"
                role="tab"
                className={active === 'bond' ? 'pf-range-tabs__btn--on' : ''}
                onClick={() => onChange('bond')}
            >
                {bondLabel}
            </button>
        </div>
    );
}
