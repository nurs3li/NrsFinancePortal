import { useCallback, useEffect, useLayoutEffect, useState, type RefObject } from 'react';

type HighlightStyle = {
  left: number;
  width: number;
  visible: boolean;
};

type HeaderInteractionsArgs = {
  navRef: RefObject<HTMLDivElement | null>;
  userMenuRef: RefObject<HTMLDivElement | null>;
  activeNavKey: string;
};

function sameHighlightStyle(a: HighlightStyle, b: HighlightStyle): boolean {
  return a.left === b.left && a.width === b.width && a.visible === b.visible;
}

function measureHighlight(navRef: RefObject<HTMLDivElement | null>, navKey: string | null): HighlightStyle {
  if (!navRef.current || !navKey) return { left: 0, width: 0, visible: false };
  const containerRect = navRef.current.getBoundingClientRect();
  const target = navRef.current.querySelector(`[data-nav-key="${navKey}"]`) as HTMLElement | null;
  if (!target) return { left: 0, width: 0, visible: false };
  const targetRect = target.getBoundingClientRect();
  return {
    left: targetRect.left - containerRect.left,
    width: targetRect.width,
    visible: true,
  };
}

export function useHeaderInteractions({ navRef, userMenuRef, activeNavKey }: HeaderInteractionsArgs) {
  const [hoveredNavKey, setHoveredNavKey] = useState<string | null>(null);
  const [highlightStyle, setHighlightStyle] = useState<HighlightStyle>({ left: 0, width: 0, visible: false });
  const [isScrolled, setIsScrolled] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);

  const syncHighlight = useCallback(() => {
    const key = hoveredNavKey || activeNavKey;
    const next = measureHighlight(navRef, key);
    setHighlightStyle((prev) => (sameHighlightStyle(prev, next) ? prev : next));
  }, [activeNavKey, hoveredNavKey, navRef]);

  useLayoutEffect(() => {
    syncHighlight();
  }, [syncHighlight]);

  useEffect(() => {
    const onResize = () => syncHighlight();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [syncHighlight]);

  useEffect(() => {
    const onScroll = () => {
      const next = window.scrollY > 6;
      setIsScrolled((prev) => (prev === next ? prev : next));
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const onClickAway = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setIsUserMenuOpen(false);
      }
    };
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsUserMenuOpen(false);
    };
    document.addEventListener('mousedown', onClickAway);
    document.addEventListener('keydown', onEscape);
    return () => {
      document.removeEventListener('mousedown', onClickAway);
      document.removeEventListener('keydown', onEscape);
    };
  }, [userMenuRef]);

  const focusNavItem = useCallback((node: HTMLElement | null) => {
    if (!node) return;
    // 'smooth' bazı tarayıcılarda rota değişimiyle çakışıp tıklamayı “yutuyor” gibi hissettirebiliyor (ağır VİOP sayfası).
    node.scrollIntoView({ behavior: 'auto', block: 'nearest', inline: 'nearest' });
  }, []);

  return {
    hoveredNavKey,
    setHoveredNavKey,
    highlightStyle,
    syncHighlight,
    isScrolled,
    isUserMenuOpen,
    setIsUserMenuOpen,
    focusNavItem,
  };
}
