import type { MacroTheme } from '../MacroTheme';
import { BondLiteracySection } from './bondLiteracy/BondLiteracySection';

type Props = {
    tokens: MacroTheme;
};

/** Makro Finans Paneli — Tahvil & Bono (eğitim / makro okuryazarlık). */
export function MacroBondsSection({ tokens }: Props) {
    return <BondLiteracySection tokens={tokens} />;
}
