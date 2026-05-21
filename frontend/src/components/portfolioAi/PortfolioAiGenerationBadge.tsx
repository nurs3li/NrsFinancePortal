import type { TranslateFn } from './portfolioAiUiTypes';

export type PortfolioAiSource = 'OPENAI' | 'FALLBACK_RULE_BASED';

type Props = {
    t: TranslateFn;
    source: PortfolioAiSource | string | null | undefined;
    model?: string | null;
};

export function PortfolioAiGenerationBadge({ t, source, model }: Props) {
    const isOpenAi = source === 'OPENAI';
    const label = isOpenAi ? 'OPENAI' : 'FALLBACK';
    const hint = isOpenAi
        ? model
            ? t('portfolioAi.badgeAiModel', 'Model: {model}').replace('{model}', model)
            : t('portfolioAi.badgeAiGenerated', 'OpenAI ile üretildi')
        : t(
              'portfolioAi.badgeFallbackHint',
              'OpenAI yanıt vermedi veya kota/limit nedeniyle yerel özet kullanıldı.',
          );

    return (
        <span
            className={`pf-ai-gen-badge${isOpenAi ? ' pf-ai-gen-badge--ai' : ' pf-ai-gen-badge--fallback'}`}
            title={hint}
        >
            {label}
        </span>
    );
}
