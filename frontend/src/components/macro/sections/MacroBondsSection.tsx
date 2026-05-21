import type { MacroTheme } from '../MacroTheme';
import { BondLiteracySection } from './bondLiteracy/BondLiteracySection';

type Props = {
    tokens: MacroTheme;
};

/** Faiz & Enflasyon Paneli — Tahvil & Bono (eğitim / makro okuryazarlık). */
export function MacroBondsSection({ tokens }: Props) {
    return <BondLiteracySection tokens={tokens} />;
}
