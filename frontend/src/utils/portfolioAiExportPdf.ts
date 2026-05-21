import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import type { AiAnalysisResult, AssetAiCommentRow, AssetInsightNarrative } from '../types/portfolioAi';
import { getNrsBrandLogoDataUrl } from './nrsBrandPdfLogo';
import { registerSimulationPdfFont, SIMULATION_PDF_FONT_FAMILY } from './simulationPdfFont';

type Labels = {
    brandTitle: string;
    title: string;
    generated: string;
    portfolioCommentary: string;
    assetPortfolioCommentary: string;
    assetBasedAnalysis: string;
    general: string;
    mainPositive: string;
    mainRisk: string;
    scenario: string;
    watchPoints: string;
    macroImpact: string;
    finalNote: string;
    assets: string;
    symbol: string;
    weight: string;
    role: string;
    impact: string;
    positive: string;
    risk: string;
    whatToWatch: string;
    footer: string;
    fontError: string;
};

type MergedAsset = {
    symbol: string;
    assetName?: string | null;
    weightPct?: number;
    returnPct?: number | null;
    role: string;
    impact: string;
    positive: string;
    risk: string;
    watch: string[];
    detail: string;
};

const PAGE = {
    left: 12,
    right: 12,
    top: 18,
    bottom: 278,
    width: 186,
};

const CARD = {
    x: PAGE.left,
    width: PAGE.width,
    labelColW: 44,
    gap: 6,
};

const COLORS = {
    cardBg: [248, 250, 252] as [number, number, number],
    cardBorder: [148, 163, 184] as [number, number, number],
    headerBg: [30, 41, 59] as [number, number, number],
    headerText: [248, 250, 252] as [number, number, number],
    label: [71, 85, 105] as [number, number, number],
    body: [51, 65, 85] as [number, number, number],
    divider: [226, 232, 240] as [number, number, number],
    positive: [22, 101, 52] as [number, number, number],
    negative: [185, 28, 28] as [number, number, number],
    badgeBg: [241, 245, 249] as [number, number, number],
};

function mergeAssetRows(result: AiAnalysisResult): MergedAsset[] {
    const map = new Map<string, MergedAsset>();

    const upsert = (symbol: string, patch: Partial<MergedAsset>) => {
        const key = symbol.toUpperCase();
        const prev = map.get(key);
        map.set(key, {
            symbol: key,
            assetName: patch.assetName ?? prev?.assetName,
            weightPct: patch.weightPct ?? prev?.weightPct,
            returnPct: patch.returnPct ?? prev?.returnPct,
            role: patch.role?.trim() || prev?.role || '',
            impact: patch.impact?.trim() || prev?.impact || '',
            positive: patch.positive?.trim() || prev?.positive || '',
            risk: patch.risk?.trim() || prev?.risk || '',
            watch: patch.watch?.length ? patch.watch : (prev?.watch ?? []),
            detail: patch.detail?.trim() || prev?.detail || '',
        });
    };

    const fromComment = (c: AssetAiCommentRow) => {
        upsert(c.symbol, {
            assetName: c.assetName,
            weightPct: c.weightPct,
            returnPct: c.returnPct,
            role: c.role ?? '',
            impact:
                c.impactOnPortfolio?.trim() ||
                c.detailComment?.trim() ||
                c.shortComment?.trim() ||
                '',
            positive:
                c.positiveView?.trim() ||
                c.positiveFactors?.find((p) => p.trim())?.trim() ||
                '',
            risk:
                c.riskView?.trim() ||
                c.riskFactors?.find((r) => r.trim())?.trim() ||
                '',
            watch: (c.whatToWatch ?? []).filter((w) => w.trim()),
            detail: c.detailComment?.trim() || c.shortComment?.trim() || '',
        });
    };

    const fromInsight = (i: AssetInsightNarrative) => {
        upsert(i.symbol, {
            assetName: i.assetName,
            weightPct: i.weightPct,
            returnPct: i.returnPct,
            role: i.role ?? '',
            impact:
                i.impactOnPortfolio?.trim() ||
                i.detailComment?.trim() ||
                i.shortComment?.trim() ||
                '',
            positive: i.positiveView?.trim() || '',
            risk: i.riskView?.trim() || '',
            watch: (i.whatToWatch ?? []).filter((w) => w.trim()),
            detail: i.detailComment?.trim() || i.shortComment?.trim() || '',
        });
    };

    (result.assetComments ?? []).forEach(fromComment);
    (result.assetInsights ?? []).forEach(fromInsight);

    return Array.from(map.values()).sort((a, b) => (b.weightPct ?? 0) - (a.weightPct ?? 0));
}

function lastTableY(doc: jsPDF): number {
    const t = doc as jsPDF & { lastAutoTable?: { finalY: number } };
    return t.lastAutoTable?.finalY ?? PAGE.top;
}

function newPageY(doc: jsPDF): number {
    doc.addPage();
    return PAGE.top;
}

function reserveY(doc: jsPDF, y: number, needed: number): number {
    if (y + needed > PAGE.bottom) {
        return newPageY(doc);
    }
    return y;
}

type CardField = { label: string; body: string };

function buildCardRows(
    fields: CardField[],
    bullets?: { label: string; items: string[] } | null,
): [string, string][] {
    const rows: [string, string][] = fields
        .filter((f) => f.body.trim())
        .map((f) => [f.label, f.body]);
    if (bullets && bullets.items.length > 0) {
        rows.push([bullets.label, bullets.items.map((i) => `• ${i}`).join('\n')]);
    }
    return rows;
}

/** Etiket + metin: autoTable satır yüksekliğini otomatik hesaplar */
function renderCardTable(doc: jsPDF, y: number, headerTitle: string, rows: [string, string][]): number {
    if (rows.length === 0) return y;
    y = reserveY(doc, y, 18);

    autoTable(doc, {
        startY: y,
        head: [
            [
                {
                    content: headerTitle,
                    colSpan: 2,
                    styles: {
                        halign: 'left',
                        font: SIMULATION_PDF_FONT_FAMILY,
                        fontStyle: 'normal',
                    },
                },
            ],
        ],
        body: rows,
        theme: 'grid',
        tableWidth: CARD.width,
        margin: { left: CARD.x, right: PAGE.right },
        styles: {
            font: SIMULATION_PDF_FONT_FAMILY,
            fontStyle: 'normal',
            fontSize: 8.5,
            cellPadding: 2.5,
            textColor: COLORS.body,
            lineColor: COLORS.divider,
            lineWidth: 0.15,
            overflow: 'linebreak',
            valign: 'top',
        },
        headStyles: {
            font: SIMULATION_PDF_FONT_FAMILY,
            fontStyle: 'normal',
            fillColor: COLORS.headerBg,
            textColor: COLORS.headerText,
            fontSize: 10,
            cellPadding: 3,
        },
        bodyStyles: {
            font: SIMULATION_PDF_FONT_FAMILY,
            fontStyle: 'normal',
            fillColor: COLORS.cardBg,
        },
        columnStyles: {
            0: {
                cellWidth: CARD.labelColW,
                fontSize: 7.5,
                textColor: COLORS.label,
            },
            1: { cellWidth: CARD.width - CARD.labelColW },
        },
    });

    return lastTableY(doc) + CARD.gap;
}

function addSectionHeading(doc: jsPDF, title: string, y: number): number {
    y = reserveY(doc, y, 10);
    doc.setFont(SIMULATION_PDF_FONT_FAMILY, 'normal');
    doc.setFontSize(11);
    doc.setTextColor(30, 41, 59);
    doc.text(title, PAGE.left, y);
    return y + 7;
}

function addPortfolioCommentaryCard(doc: jsPDF, labels: Labels, result: AiAnalysisResult, y: number): number {
    const overview = result.portfolioOverview;
    const decision = result.decisionPerspective;
    const fields: CardField[] = [];

    const general =
        overview?.summary?.trim() ||
        overview?.currentSituation?.trim() ||
        result.summary?.trim() ||
        '';
    if (general) fields.push({ label: labels.general, body: general });
    if (overview?.mainPositive?.trim()) {
        fields.push({ label: labels.mainPositive, body: overview.mainPositive });
    }
    if (overview?.mainRisk?.trim()) {
        fields.push({ label: labels.mainRisk, body: overview.mainRisk });
    }
    const scenario = decision?.comment?.trim() || result.scenarioComment?.trim() || '';
    if (scenario) fields.push({ label: labels.scenario, body: scenario });
    const macro = result.macroAndNewsImpact?.summary?.trim();
    if (macro) fields.push({ label: labels.macroImpact, body: macro });
    if (result.finalNote?.trim()) fields.push({ label: labels.finalNote, body: result.finalNote });

    const watchItems =
        decision?.watchPoints?.filter((w) => w.trim()) ?? result.keyFindings ?? [];
    const bullets =
        watchItems.length > 0 ? { label: labels.watchPoints, items: watchItems } : null;

    if (fields.length === 0 && !bullets) return y;
    return renderCardTable(doc, y, labels.portfolioCommentary, buildCardRows(fields, bullets));
}

function assetHeaderTitle(asset: MergedAsset): string {
    const name = asset.assetName?.trim();
    if (name && name.toUpperCase() !== asset.symbol) {
        return `${asset.symbol} — ${name}`;
    }
    return asset.symbol;
}

function assetBadges(asset: MergedAsset, labels: Labels): string[] {
    const badges: string[] = [];
    if (asset.weightPct != null && Number.isFinite(asset.weightPct)) {
        badges.push(`${labels.weight} %${asset.weightPct.toFixed(1)}`);
    }
    if (asset.returnPct != null && Number.isFinite(asset.returnPct)) {
        badges.push(`${asset.returnPct >= 0 ? '+' : ''}${asset.returnPct.toFixed(2)}%`);
    }
    return badges;
}

function addAssetCard(doc: jsPDF, asset: MergedAsset, labels: Labels, y: number): number {
    const fields: CardField[] = [];
    if (asset.role) fields.push({ label: labels.role, body: asset.role });
    if (asset.impact) fields.push({ label: labels.impact, body: asset.impact });
    if (asset.positive) fields.push({ label: labels.positive, body: asset.positive });
    if (asset.risk) fields.push({ label: labels.risk, body: asset.risk });
    if (asset.detail && asset.detail !== asset.impact) {
        fields.push({ label: labels.assets, body: asset.detail });
    }
    const bullets =
        asset.watch.length > 0 ? { label: labels.whatToWatch, items: asset.watch } : undefined;
    if (fields.length === 0 && !bullets) return y;

    const badges = assetBadges(asset, labels);
    const header =
        badges.length > 0
            ? `${assetHeaderTitle(asset)}  ·  ${badges.join('  ·  ')}`
            : assetHeaderTitle(asset);
    return renderCardTable(doc, y, header, buildCardRows(fields, bullets));
}

export async function exportPortfolioAiPdf(
    result: AiAnalysisResult,
    labels: Labels,
    locale: string,
): Promise<void> {
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
    try {
        await registerSimulationPdfFont(doc);
        await getNrsBrandLogoDataUrl();
    } catch {
        window.alert(labels.fontError);
        return;
    }

    doc.setFont(SIMULATION_PDF_FONT_FAMILY, 'normal');

    try {
        const logo = await getNrsBrandLogoDataUrl();
        doc.addImage(logo, 'PNG', PAGE.left, 8, 72, 14);
    } catch {
        doc.setFontSize(11);
        doc.setTextColor(30, 30, 40);
        doc.text(labels.brandTitle, PAGE.left, 16);
    }

    doc.setFontSize(8);
    doc.setTextColor(100, 100, 110);
    doc.text(labels.brandTitle, PAGE.left, 24);

    doc.setFontSize(12);
    doc.setTextColor(25, 25, 30);
    doc.text(labels.title, PAGE.left, 32);
    if (result.title) {
        doc.setFontSize(10);
        doc.text(result.title, PAGE.left, 38);
    }
    doc.setFontSize(8);
    doc.setTextColor(90, 90, 90);
    const metaY = result.title ? 44 : 38;
    doc.text(
        `${labels.generated}: ${new Date(result.createdAt).toLocaleString(locale)} · ${result.source ?? ''} · P:${result.portfolioScore} R:${result.riskScore}`,
        PAGE.left,
        metaY,
    );

    let y = metaY + 12;
    y = addPortfolioCommentaryCard(doc, labels, result, y);

    const assets = mergeAssetRows(result);

    if (assets.length > 0) {
        y = addSectionHeading(doc, labels.assetPortfolioCommentary, y);
        const summaryRows = assets.map((a) => [
            a.symbol,
            a.weightPct != null ? `%${a.weightPct.toFixed(1)}` : '—',
            (a.role || '—').slice(0, 60),
            (a.impact || a.detail || '—').slice(0, 120),
        ]);
        y = reserveY(doc, y, 20);
        autoTable(doc, {
            startY: y,
            head: [[labels.symbol, labels.weight, labels.role, labels.impact]],
            body: summaryRows,
            styles: {
                font: SIMULATION_PDF_FONT_FAMILY,
                fontStyle: 'normal',
                fontSize: 7.5,
                cellPadding: 2,
                textColor: [51, 65, 85],
                lineColor: [226, 232, 240],
                lineWidth: 0.2,
            },
            headStyles: {
                font: SIMULATION_PDF_FONT_FAMILY,
                fontStyle: 'normal',
                fillColor: [30, 41, 59],
                textColor: [248, 250, 252],
            },
            alternateRowStyles: { fillColor: [248, 250, 252] },
            margin: { left: PAGE.left, right: PAGE.right },
            tableWidth: PAGE.width,
        });
        y = lastTableY(doc) + CARD.gap + 2;

        y = addSectionHeading(doc, labels.assetBasedAnalysis, y);
        for (const asset of assets) {
            y = addAssetCard(doc, asset, labels, y);
        }
    }

    y = reserveY(doc, y, 8);
    doc.setFontSize(7);
    doc.setTextColor(100, 100, 100);
    doc.text(labels.footer, PAGE.left, Math.min(y, PAGE.bottom - 4), { maxWidth: PAGE.width });

    const safeName = (result.title || 'portfoy-ai')
        .replace(/[^\w\u00C0-\u024F\s-]+/gi, '')
        .trim()
        .replace(/\s+/g, '-')
        .slice(0, 40);
    doc.save(`${safeName || 'portfoy-ai'}-${new Date().toISOString().slice(0, 10)}.pdf`);
}
