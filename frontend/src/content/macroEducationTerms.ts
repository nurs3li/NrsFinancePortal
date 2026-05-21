export type MacroEducationTerm = {
    title: string;
    short: string;
    detail: string;
    formula?: string;
    example?: string;
    whyItMatters?: string;
    category: 'inflation' | 'rates' | 'deposit' | 'credit' | 'bond' | 'eurobond' | 'risk';
};

export type MacroTermId = keyof typeof macroEducationTerms;

export const macroEducationTerms = {
    pageIntro: {
        title: 'Faiz & Enflasyon Paneli Nedir?',
        short: 'Makro göstergeleri finansal okuryazarlık odağıyla sunan panel.',
        detail:
            'Bu sayfa enflasyon, TCMB politika faizi, mevduat ve kredi faizleri ile tahvil/eurobond istatistiklerini bir arada gösterir. Amaç ham veri listesi değil; göstergeler arası ilişkiyi anlamaktır.',
        whyItMatters: 'Makro ortam, tasarruf, borçlanma ve yatırım kararlarının arka planını şekillendirir.',
        category: 'rates',
    },
    cpi: {
        title: 'TÜFE Nedir?',
        short: 'Tüketici fiyatlarındaki genel değişimi ölçen endekstir.',
        detail:
            'Tüketici Fiyat Endeksi (TÜFE), hanehalkının satın aldığı mal ve hizmet sepetinin fiyat seviyesindeki değişimi izler. Aylık ve yıllık enflasyon oranları bu endeksten türetilir.',
        whyItMatters: 'Enflasyonun en yaygın takip edilen göstergesidir; reel getiri hesaplarında kullanılır.',
        category: 'inflation',
    },
    ppi: {
        title: 'Yİ-ÜFE Nedir?',
        short: 'Üretici fiyatlarındaki değişimi ölçen endekstir.',
        detail:
            'Yurt İçi Üretici Fiyat Endeksi (Yİ-ÜFE), üretim aşamasındaki maliyet baskısını yansıtır. TÜFE’den önce sinyal verebilir.',
        whyItMatters: 'Üretim maliyetleri tüketici fiyatlarına zamanla yansıyabilir.',
        category: 'inflation',
    },
    mom: {
        title: 'Aylık Değişim (MoM) Nedir?',
        short: 'Bir önceki aya göre yüzde değişimdir.',
        detail: 'Month-over-Month (MoM), endeks veya fiyatın son ay içindeki değişimidir.',
        example: 'TÜFE MoM %2 ise fiyat sepeti bir ayda ortalama %2 artmıştır.',
        category: 'inflation',
    },
    yoy: {
        title: 'Yıllık Değişim (YoY) Nedir?',
        short: 'Aynı ay bir yıl öncesine göre yüzde değişimdir.',
        detail: 'Year-over-Year (YoY), mevsimsellikten arındırılmış yıllık enflasyon karşılaştırmasıdır.',
        category: 'inflation',
    },
    indexLevelChart: {
        title: 'Endeks Seviyesi Grafiği Nedir?',
        short: 'Doğrudan enflasyon oranı değil, fiyat seviyesi endeksini gösterir.',
        detail:
            'Grafikteki çizgiler TÜFE ve Yİ-ÜFE endeks düzeyidir. Enflasyon oranı, bu endekslerin aylık veya yıllık değişiminden hesaplanır.',
        whyItMatters: 'Endeks trendi uzun dönem fiyat seviyesini; MoM/YoY kartları ise güncel enflasyon hızını özetler.',
        category: 'inflation',
    },
    policyRate: {
        title: 'TCMB Politika Faizi Nedir?',
        short: 'Merkez bankasının kısa vadeli faiz politikasının ana referansıdır.',
        detail:
            'Politika faizi, TL para piyasasındaki temel referans faizdir. Mevduat ve kredi faizleri bu seviyenin etrafında şekillenir.',
        whyItMatters: 'Faiz artışı genelde TL cazibesini artırır; düşüş ise kredi ve varlık fiyatlarını destekleyebilir.',
        category: 'rates',
    },
    fundingCost: {
        title: 'Ortalama Fonlama Maliyeti Nedir?',
        short: 'Bankaların TL fonlama maliyetinin ağırlıklı ortalamasıdır.',
        detail: 'TCMB’nin yayımladığı ortalama fonlama maliyeti, bankacılık sisteminin fon temini maliyetini özetler.',
        whyItMatters: 'Mevduat ve kredi fiyatlamasında dolaylı bir maliyet göstergesidir.',
        category: 'rates',
    },
    realPolicyRate: {
        title: 'Reel Politika Faizi Nedir?',
        short: 'Politika faizinin enflasyon etkisinden arındırılmış halidir.',
        detail:
            'Yaklaşık olarak politika faizi eksi yıllık enflasyon şeklinde yorumlanır. Pozitif reel faiz TL varlıklarını daha cazip kılabilir; negatif reel faiz alım gücü kaybına işaret edebilir.',
        formula: 'Reel faiz ≈ Politika faizi − Enflasyon (YoY)',
        category: 'rates',
    },
    depositRate: {
        title: 'TL Mevduat Faizi Nedir?',
        short: 'Bankaya bırakılan paranın vadeli nominal getirisidir.',
        detail: 'Mevduat faizi vade uzunluğuna göre değişir (1 ay, 3 ay, 6 ay, 1 yıl). Gösterilen oranlar genelde haftalık EVDS akım ortalamalarıdır.',
        category: 'deposit',
    },
    nominalReturn: {
        title: 'Nominal Getiri Nedir?',
        short: 'Enflasyon düşülmeden önceki yüzde getiridir.',
        detail: 'Örneğin %40 mevduat faizi nominal getiridir; enflasyon %50 ise alım gücü düşebilir.',
        category: 'deposit',
    },
    realReturn: {
        title: 'Reel Getiri Nedir?',
        short: 'Enflasyondan arındırılmış gerçek getiridir.',
        detail: 'Nominal getiriden enflasyon düşülerek hesaplanır; alım gücünüzün gerçekten artıp artmadığını gösterir.',
        formula: 'Reel getiri ≈ ((1 + nominal) / (1 + enflasyon)) − 1',
        whyItMatters: 'Nominal kazanç olsa bile enflasyon daha yüksekse alım gücü azalır.',
        category: 'deposit',
    },
    maturity: {
        title: 'Vade Nedir?',
        short: 'Paranın bankada veya borçta bağlı kalacağı süredir.',
        detail: 'Kısa vade daha likit; uzun vade genelde daha yüksek faiz sunabilir ancak erişim kısıtlıdır.',
        category: 'deposit',
    },
    bondMaturity: {
        title: 'Vade',
        short: 'Anaparanın geri ödeneceği tarih.',
        detail:
            'Tahvil veya bononun anapara geri ödeme tarihidir. Vade uzadıkça faiz, enflasyon ve risk beklentilerine duyarlılık artabilir.',
        category: 'bond',
    },
    loanRate: {
        title: 'Kredi Faizi Nedir?',
        short: 'Borçlanma maliyetini gösteren yüzde orandır.',
        detail: 'Kredi faizleri yatırım getirisi değildir; bankanın yeni açılan kredilere uyguladığı ortalama faizlerdir.',
        whyItMatters: 'Yüksek kredi faizi tüketim ve yatırım talebini baskılayabilir.',
        category: 'credit',
    },
    creditSpread: {
        title: 'Kredi-Mevduat Makası Nedir?',
        short: 'Kredi faizi ile mevduat faizi arasındaki farktır.',
        detail: 'Bankacılık sisteminde bu fark, aracılık marjının bir parçasını yansıtır.',
        category: 'credit',
    },
    bondSectionIntro: {
        title: 'Tahvil & Bono Bölümü',
        short: 'Eğitim ve makro bağlantı modülü.',
        detail:
            'Bu bölüm canlı tahvil terminali değildir. Tahvil ve bononun temel mantığını, TCMB politika faizi ve enflasyonla ilişkisini anlatır. Canlı ISIN bazlı fiyatlar, piyasa değerleri ve performans grafikleri Piyasalar > Tahvil sekmesinde takip edilir.',
        category: 'bond',
    },
    bondMacroDrivers: {
        title: 'Tahvil/Bono Faizleri Nelerden Etkilenir?',
        short: 'Kısa vade politika faizi; uzun vade enflasyon beklentisi ve risk primi.',
        detail:
            'Kısa vadeli araçlar (bono) TCMB politika faizine ve kısa vadeli TL faiz ortamına daha yakın tepki verir. Uzun vadeli tahviller ise enflasyon beklentisi, büyüme görünümü ve risk primi (kur, likidite, kredi riski) gibi faktörlerden daha fazla etkilenir. Bu panelde canlı getiri eğrisi yoktur; ilişki kavramsal olarak gösterilir.',
        category: 'bond',
    },
    bond: {
        title: 'Tahvil',
        short: 'Genellikle 1 yıldan uzun vadeli borçlanma aracı.',
        detail:
            'Devletin veya şirketlerin genellikle 1 yıldan uzun vadeli borçlanmak için çıkardığı menkul kıymettir. Yatırımcı tahvil aldığında ihraççıya borç vermiş olur.',
        category: 'bond',
    },
    bono: {
        title: 'Bono',
        short: 'Genellikle 1 yıldan kısa vadeli borçlanma aracı.',
        detail:
            'Genellikle 1 yıldan kısa vadeli borçlanma aracıdır. Mantığı tahvile benzer, ancak vadesi daha kısadır.',
        category: 'bond',
    },
    coupon: {
        title: 'Kupon',
        short: 'Dönemsel faiz ödemesi.',
        detail:
            'Tahvilin belirli dönemlerde yatırımcıya ödediği faizdir. Kupon oranı, tahvilin piyasa getirisiyle aynı şey değildir.',
        category: 'bond',
    },
    yieldCurve: {
        title: 'Vade-Getiri Eğrisi',
        short: 'Farklı vadelerdeki borçlanma getirilerini karşılaştırır.',
        detail:
            'Farklı vadelerdeki borçlanma araçlarının getiri seviyelerini karşılaştıran eğridir. Kısa vadeler politika faizinden, uzun vadeler enflasyon beklentisi ve risk priminden daha fazla etkilenebilir.',
        category: 'bond',
    },
    bondPriceYield: {
        title: 'Faiz-Fiyat İlişkisi',
        short: 'Piyasa faizi ile tahvil fiyatı ters yönlü hareket edebilir.',
        detail:
            'Piyasa faizleri yükseldiğinde eski düşük getirili tahvillerin fiyatı düşebilir. Piyasa faizleri düştüğünde ise eski yüksek getirili tahviller daha değerli hale gelebilir.',
        example:
            'Yeni tahviller daha yüksek faiz verirse, eski düşük kuponlu tahviller aynı fiyattan cazip olmaz. Bu nedenle eski tahvilin piyasa fiyatı düşerek yeni getiri seviyesine yaklaşır.',
        category: 'bond',
    },
    eurobond: {
        title: 'Eurobond Nedir?',
        short: 'Döviz cinsinden ihraç edilen uzun vadeli borçlanma senedidir.',
        detail: 'Genelde ABD doları veya euro cinsinden uluslararası piyasalarda işlem görür; kur riski taşır.',
        category: 'eurobond',
    },
    bookValue: {
        title: 'Yazılı Değer Nedir?',
        short: 'Kayıtlı nominal/stok değeridir; piyasa fiyatı değildir.',
        detail: 'EVDS’teki yazılı değer, borcun muhasebe kayıtlarındaki tutarı yansıtır.',
        category: 'eurobond',
    },
    marketValue: {
        title: 'Piyasa Değeri Nedir?',
        short: 'Stokun güncel piyasa koşullarına göre değerlemesidir.',
        detail: 'Tekil işlem fiyatı değil; toplam stokun piyasa değerlemesidir.',
        category: 'eurobond',
    },
    remainingMaturity: {
        title: 'Kalan Vade Nedir?',
        short: 'Bugünden itibaren ödenmesi beklenen süreyi ifade eder.',
        detail: 'Kısa kalan vade daha az faiz riski; uzun kalan vade fiyat dalgalanmasına daha duyarlı olabilir.',
        category: 'eurobond',
    },
    currencyMix: {
        title: 'Para Birimi Dağılımı Nedir?',
        short: 'Stokun USD, EUR, JPY vb. para birimlerine göre dağılımıdır.',
        detail: 'Kur riski ve portföy çeşitliliği açısından önemlidir.',
        category: 'eurobond',
    },
    eurobondComposition: {
        title: 'Genel Yönetim Eurobond Kompozisyonu',
        short: 'Tekil Eurobond fiyat ekranı değil; EVDS stok/kompozisyon verisidir.',
        detail:
            'Bu bölüm TCMB EVDS üzerinden yayımlanan Genel Yönetim Eurobondları toplam yazılı değer, piyasa değeri, vade ve para birimi dağılımı istatistiklerini gösterir. Canlı alım-satım fiyatı içermez.',
        category: 'eurobond',
    },
    liquidity: {
        title: 'Likidite Nedir?',
        short: 'Varlığın hızlı ve düşük maliyetle nakde çevrilebilmesidir.',
        detail: 'Kısa vadeli mevduat daha likit; uzun vadeli tahvil satışı piyasa koşullarına bağlıdır.',
        category: 'risk',
    },
    spread: {
        title: 'Spread (Makas) Nedir?',
        short: 'İki faiz veya getiri arasındaki farktır.',
        detail: 'Örneğin kredi faizi ile mevduat faizi arasındaki fark bankacılık makasını gösterir.',
        category: 'risk',
    },
    purchasingPower: {
        title: 'Satın Alma Gücü',
        short: 'Paranın gerçekte kaç mal ve hizmet alabildiğidir.',
        detail: 'Enflasyon satın alma gücünü eritir; reel getiri pozitifse güç korunabilir.',
        category: 'inflation',
    },
    yield: {
        title: 'Getiri (Yield) Nedir?',
        short: 'Borçlanma aracının getiri oranıdır.',
        detail: 'Eurobond ve tahvilde getiri, kupon, fiyat ve vadeye bağlı olarak hesaplanır; piyasa koşullarına göre değişir.',
        category: 'eurobond',
    },
    fxRisk: {
        title: 'Kur Riski Nedir?',
        short: 'Döviz kuru değişiminin değeri etkilemesidir.',
        detail: 'TL ile borçlanmadığınız Eurobond pozisyonlarında kur hareketi TL bazında getiriyi değiştirir.',
        category: 'risk',
    },
    interestRateRisk: {
        title: 'Faiz Riski Nedir?',
        short: 'Faiz oranı değişiminin tahvil fiyatını etkilemesidir.',
        detail: 'Faizler yükseldiğinde mevcut sabit kuponlu tahvillerin piyasa fiyatı genelde düşer.',
        category: 'risk',
    },
    borrowingCost: {
        title: 'Borçlanma Maliyeti Nedir?',
        short: 'Kredi kullanırken ödediğiniz faiz maliyetidir.',
        detail: 'Kredi faizleri yatırım getirisi değildir; ne kadar borçlanırsanız o kadar faiz ödersiniz.',
        whyItMatters: 'Yüksek borçlanma maliyeti bütçe ve yatırım planlarını doğrudan etkiler.',
        category: 'credit',
    },
} as const satisfies Record<string, MacroEducationTerm>;

export const glossaryCategories = [
    { id: 'inflation' as const, label: 'Enflasyon' },
    { id: 'rates' as const, label: 'Faiz' },
    { id: 'deposit' as const, label: 'Mevduat' },
    { id: 'credit' as const, label: 'Kredi' },
    { id: 'bond' as const, label: 'Tahvil/Bono' },
    { id: 'eurobond' as const, label: 'Eurobond' },
    { id: 'risk' as const, label: 'Risk' },
];
