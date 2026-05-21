import { useEffect, useState } from 'react';
import type { TranslateFn } from './portfolioAiUiTypes';

/** Koşan ASCII robot — analiz beklerken döner */
const RUN_FRAMES = [
    `
  .---.
  |o_o|
  | + |
 /|   |\\
  |   |
 /     \\
    *`,
    `
  .---.
  |o_o|
  | + |
  |   |\\
 /     \\
  *`,
    `
  .---.
  |o_o|
  | + |
  |   |/
 /     \\
   +`,
    `
  .---.
  |o_o|
  | + |
 /|   |
  |   |
 /     \\
  .`,
    `
  .---.
  |o_o|
  | + |
  \\|   |
  |   |
 /     \\
 +`,
];

const TRAIL = ['-.', '-.+', '-.+*', '-.+*/', '-.+*/ '];

type Props = {
    active: boolean;
    t: TranslateFn;
};

export function PortfolioAiAsciiRunner({ active, t }: Props) {
    const [frame, setFrame] = useState(0);
    const [trail, setTrail] = useState(0);

    useEffect(() => {
        if (!active) {
            setFrame(0);
            setTrail(0);
            return;
        }
        const runId = setInterval(
            () => setFrame((f) => (f + 1) % RUN_FRAMES.length),
            160,
        );
        const trailId = setInterval(() => setTrail((t) => (t + 1) % TRAIL.length), 280);
        return () => {
            clearInterval(runId);
            clearInterval(trailId);
        };
    }, [active]);

    if (!active) {
        return null;
    }

    return (
        <div
            className="pf-ai-ascii-runner"
            role="status"
            aria-live="polite"
            aria-label={t('portfolioAi.asciiRunnerAria', 'Analiz oluşturuluyor')}
        >
            <pre className="pf-ai-ascii-runner__art">{RUN_FRAMES[frame]}</pre>
            <p className="pf-ai-ascii-runner__trail" aria-hidden>
                {TRAIL[trail].repeat(6)}
            </p>
            <p className="pf-ai-ascii-runner__caption">
                {t('portfolioAi.asciiRunnerCaption', 'Portföy taranıyor, robot koşuyor…')}
            </p>
        </div>
    );
}
