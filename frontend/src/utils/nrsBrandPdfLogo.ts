/** NRS Finance Portal mark + lockup rasterized for jsPDF (SVG → PNG data URL). */
const BRAND_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="56" viewBox="0 0 320 56">
  <defs>
    <linearGradient id="g" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#1d4ed8"/>
      <stop offset="55%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#7dd3fc"/>
    </linearGradient>
  </defs>
  <rect x="2" y="30" width="3.5" height="12" rx="0.8" fill="url(#g)"/>
  <rect x="7.5" y="26" width="3.5" height="16" rx="0.8" fill="url(#g)"/>
  <rect x="13" y="22" width="3.5" height="20" rx="0.8" fill="url(#g)"/>
  <text x="22" y="28" fill="#f8fafc" font-size="14" font-weight="800" font-family="Inter, Arial, sans-serif" letter-spacing="0.04em">NRS</text>
  <path fill="none" stroke="url(#g)" stroke-width="2.2" stroke-linecap="round" d="M 20 33 Q 29 22 41 14 L 47 9"/>
  <path fill="url(#g)" d="M 43.5 7.5 L 49 8.8 L 45.8 13.5 Z"/>
  <text x="58" y="24" fill="#f8fafc" font-size="13" font-weight="800" font-family="Inter, Arial, sans-serif" letter-spacing="0.12em">NRS FINANCE</text>
  <text x="58" y="40" fill="#7dd3fc" font-size="8" font-weight="600" font-family="Inter, Arial, sans-serif" letter-spacing="0.32em">PORTAL</text>
</svg>`;

let cachedLogoDataUrl: string | null = null;

export async function getNrsBrandLogoDataUrl(): Promise<string> {
    if (cachedLogoDataUrl) {
        return cachedLogoDataUrl;
    }
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => {
            const canvas = document.createElement('canvas');
            canvas.width = 640;
            canvas.height = 112;
            const ctx = canvas.getContext('2d');
            if (!ctx) {
                reject(new Error('Canvas not supported'));
                return;
            }
            ctx.fillStyle = '#0f172a';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            cachedLogoDataUrl = canvas.toDataURL('image/png');
            resolve(cachedLogoDataUrl);
        };
        img.onerror = () => reject(new Error('Brand logo render failed'));
        img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(BRAND_SVG)}`;
    });
}
