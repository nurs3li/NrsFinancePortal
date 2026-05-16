import { useEffect, useRef, useState } from 'react';

export function useAnimatedNumber(target: number) {
    const [display, setDisplay] = useState(0);
    const displayRef = useRef(0);

    useEffect(() => {
        const from = displayRef.current;
        let raf = 0;
        const t0 = performance.now();
        const dur = 680;
        const step = (now: number) => {
            const p = Math.min(1, (now - t0) / dur);
            const eased = 1 - (1 - p) ** 3;
            const next = from + (target - from) * eased;
            displayRef.current = next;
            setDisplay(next);
            if (p < 1) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [target]);

    return display;
}
