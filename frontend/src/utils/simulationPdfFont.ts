import type { jsPDF } from 'jspdf';

const FONT_FILE = 'NotoSans-Regular.ttf';
/** jsPDF iç VFS ve font ailesi adı */
export const SIMULATION_PDF_FONT_FAMILY = 'NotoSans';

function uint8ToBinaryString(bytes: Uint8Array): string {
    const chunk = 0x8000;
    let s = '';
    for (let i = 0; i < bytes.length; i += chunk) {
        const sub = bytes.subarray(i, i + chunk);
        s += String.fromCharCode.apply(null, sub as unknown as number[]);
    }
    return s;
}

/**
 * Türkçe (ş, ğ, ı, ö, ü, ç, İ) için Noto Sans yükler.
 * Dosya: `public/fonts/NotoSans-Regular.ttf`
 */
export async function registerSimulationPdfFont(doc: jsPDF): Promise<void> {
    const url = `${import.meta.env.BASE_URL}fonts/${FONT_FILE}`;
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`Font HTTP ${res.status}: ${url}`);
    }
    const buf = await res.arrayBuffer();
    const bin = uint8ToBinaryString(new Uint8Array(buf));
    doc.addFileToVFS(FONT_FILE, bin);
    doc.addFont(FONT_FILE, SIMULATION_PDF_FONT_FAMILY, 'normal', undefined, 'Identity-H');
    doc.setFont(SIMULATION_PDF_FONT_FAMILY, 'normal');
}
