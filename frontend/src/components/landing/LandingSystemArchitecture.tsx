import { FileText } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import {
    ARCH_CARDS,
    ARCH_DOCS_LABEL,
    ARCH_DOCS_URL,
    ARCH_SECTION_INTRO,
    ARCH_SECTION_TITLE,
    type ArchLang,
} from './landingArchitectureData';
import './LandingSystemArchitecture.css';

export function LandingSystemArchitecture() {
    const { lang } = useLanguage();
    const archLang: ArchLang = lang === 'en' ? 'en' : 'tr';

    return (
        <section id="architecture" className="landing-system-arch landing-reveal">
            <h2 className="landing-system-arch__title">{ARCH_SECTION_TITLE[archLang]}</h2>
            <p className="landing-system-arch__intro">{ARCH_SECTION_INTRO[archLang]}</p>

            <div className="landing-system-arch__grid">
                {ARCH_CARDS.map((card) => {
                    const Icon = card.icon;
                    return (
                        <article key={card.id} className="landing-arch-card">
                            <span className="landing-arch-card__icon" aria-hidden>
                                <Icon size={20} strokeWidth={1.35} />
                            </span>
                            <h3 className="landing-arch-card__title">{card.title[archLang]}</h3>
                            <p className="landing-arch-card__body">{card.body[archLang]}</p>
                        </article>
                    );
                })}
            </div>

            <div className="landing-system-arch__docs-wrap">
                <a
                    href={ARCH_DOCS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="landing-system-arch__docs-btn"
                >
                    <FileText size={16} strokeWidth={1.5} aria-hidden />
                    {ARCH_DOCS_LABEL[archLang]}
                </a>
            </div>
        </section>
    );
}
