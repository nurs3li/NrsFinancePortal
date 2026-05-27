import type { AssetType } from '../constants/OrderConstants';

export type SimulationAssetType = AssetType | 'TR_FUND';

export const SIMULATION_ASSET_TYPES: readonly SimulationAssetType[] = [
    'CRYPTO',
    'FX',
    'METAL',
    'TR_FUND',
    'FUND',
    'STOCK',
    'BIST',
];

export function backendAssetTypeForSimulation(type: SimulationAssetType): AssetType {
    return type === 'TR_FUND' ? 'FUND' : type;
}

export function displaySimulationAssetType(
    assetType: AssetType,
    pickerAssetType?: SimulationAssetType | null,
): SimulationAssetType {
    return pickerAssetType ?? assetType;
}
