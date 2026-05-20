import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import { HeroSlideMultiAsset } from './HeroSlideContent';
import {
    HeroSlideAlerts,
    HeroSlideHeatmap,
    HeroSlideMissedOpportunity,
    HeroSlideRealReturn,
    HeroSlideTimeMachine,
} from './heroSlides2';
import './LandingHeroCarousel.css';

const SLIDE_INTERVAL_MS = 2000;

const SLIDES: { id: string; render: () => ReactElement }[] = [
    { id: 'terminal', render: () => <HeroSlideMultiAsset /> },
    { id: 'heatmap', render: () => <HeroSlideHeatmap /> },
    { id: 'missed', render: () => <HeroSlideMissedOpportunity /> },
    { id: 'real-return', render: () => <HeroSlideRealReturn /> },
    { id: 'time-machine', render: () => <HeroSlideTimeMachine /> },
    { id: 'alerts', render: () => <HeroSlideAlerts /> },
];

export function LandingHeroCarousel() {
    const { t, lang } = useLanguage();
    const [activeIndex, setActiveIndex] = useState(0);

    useEffect(() => {
        setActiveIndex(0);
    }, [lang]);
    const [paused, setPaused] = useState(false);
    const touchStartX = useRef<number | null>(null);

    const goTo = useCallback((index: number) => {
        const n = SLIDES.length;
        setActiveIndex(((index % n) + n) % n);
    }, []);

    const next = useCallback(() => goTo(activeIndex + 1), [activeIndex, goTo]);
    const prev = useCallback(() => goTo(activeIndex - 1), [activeIndex, goTo]);

    useEffect(() => {
        if (paused) return;
        const reduceMotion =
            typeof window !== 'undefined' &&
            window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
        if (reduceMotion) return;
        const id = window.setInterval(next, SLIDE_INTERVAL_MS);
        return () => window.clearInterval(id);
    }, [paused, next]);

    const onKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'ArrowRight') {
            e.preventDefault();
            next();
        } else if (e.key === 'ArrowLeft') {
            e.preventDefault();
            prev();
        }
    };

    return (
        <div
            className="landing-hero-carousel"
            onMouseEnter={() => setPaused(true)}
            onMouseLeave={() => setPaused(false)}
            onKeyDown={onKeyDown}
            role="region"
            aria-roledescription="carousel"
            aria-label={t('landing.heroCarouselLabel', 'Ürün önizlemeleri')}
        >
            <div className="landing-hero-carousel__frame">
                <div
                    className="landing-hero-carousel__viewport"
                    onTouchStart={(e) => {
                        touchStartX.current = e.changedTouches[0]?.clientX ?? null;
                    }}
                    onTouchEnd={(e) => {
                        const start = touchStartX.current;
                        touchStartX.current = null;
                        if (start == null) return;
                        const dx = (e.changedTouches[0]?.clientX ?? start) - start;
                        if (Math.abs(dx) < 40) return;
                        if (dx < 0) next();
                        else prev();
                    }}
                >
                    <div
                        className="landing-hero-carousel__track"
                        style={{ transform: `translate3d(-${activeIndex * 100}%, 0, 0)` }}
                    >
                        {SLIDES.map((slide, i) => (
                            <div
                                key={slide.id}
                                className="landing-hero-carousel__slide"
                                aria-hidden={i !== activeIndex}
                            >
                                {slide.render()}
                            </div>
                        ))}
                    </div>
                </div>
            </div>
            <div className="landing-hero-carousel__dots" role="tablist">
                {SLIDES.map((slide, i) => (
                    <button
                        key={slide.id}
                        type="button"
                        role="tab"
                        aria-selected={i === activeIndex}
                        aria-label={`${t('landing.heroCarouselDot', 'Slayt')} ${i + 1}`}
                        className={`landing-hero-carousel__dot${i === activeIndex ? ' is-active' : ''}`}
                        onClick={() => goTo(i)}
                    />
                ))}
            </div>
        </div>
    );
}
