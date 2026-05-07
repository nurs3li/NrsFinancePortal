export type InstrumentMeta = {
  name: string;
  sector: string;
  exchange: string;
  description: string;
  country?: string;
};

export const instrumentMeta: Record<string, InstrumentMeta> = {
  GOOGL: {
    name: "Alphabet Inc.",
    sector: "Teknoloji",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "Google’ın ana şirketidir. Reklam, bulut ve yapay zeka alanlarında faaliyet gösterir."
  },
  META: {
    name: "Meta Platforms Inc.",
    sector: "Teknoloji",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "Facebook, Instagram ve WhatsApp’ın sahibidir. Sosyal medya ve metaverse alanında faaliyet gösterir."
  },
  AMZN: {
    name: "Amazon.com Inc.",
    sector: "E-Ticaret",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "Dünyanın en büyük e-ticaret ve bulut bilişim şirketlerinden biridir."
  },
  MSFT: {
    name: "Microsoft Corp.",
    sector: "Teknoloji",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "Yazılım, bulut ve yapay zeka alanında global lider şirketlerden biridir."
  },
  AAPL: {
    name: "Apple Inc.",
    sector: "Teknoloji",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "iPhone, Mac ve diğer tüketici elektroniği ürünleriyle tanınır."
  },
  NVDA: {
    name: "NVIDIA Corp.",
    sector: "Yarı İletken",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "GPU ve yapay zeka donanımlarıyla öne çıkan lider teknoloji şirketidir."
  },
  TSLA: {
    name: "Tesla Inc.",
    sector: "Otomotiv",
    exchange: "NASDAQ",
    country: "ABD",
    description:
      "Elektrikli araç ve enerji çözümleri geliştiren yenilikçi bir şirkettir."
  },
  WMT: {
    name: "Walmart Inc.",
    sector: "Perakende",
    exchange: "NYSE",
    country: "ABD",
    description:
      "Dünyanın en büyük perakende zincirlerinden biridir."
  },
  JPM: {
    name: "JPMorgan Chase & Co.",
    sector: "Finans",
    exchange: "NYSE",
    country: "ABD",
    description:
      "Dünyanın en büyük yatırım bankalarından biridir."
  },
  JNJ: {
    name: "Johnson & Johnson",
    sector: "Sağlık",
    exchange: "NYSE",
    country: "ABD",
    description:
      "İlaç, medikal cihaz ve tüketici sağlık ürünleri üretir."
  },
  V: {
    name: "Visa Inc.",
    sector: "Finans",
    exchange: "NYSE",
    country: "ABD",
    description:
      "Küresel ödeme sistemleri ve kredi kartı altyapısı sağlar."
  }
};

export type CryptoMeta = {
  name: string;
  symbol: string;
  category: string;
  description: string;
};

export const cryptoMeta: Record<string, CryptoMeta> = {
  BTCUSDT: {
    name: "Bitcoin",
    symbol: "BTC",
    category: "Store of Value",
    description: "İlk ve en büyük kripto para. Dijital altın olarak kabul edilir."
  },
  ETHUSDT: {
    name: "Ethereum",
    symbol: "ETH",
    category: "Smart Contract",
    description: "Akıllı kontratlar ve DeFi uygulamaları için kullanılan blockchain."
  },
  BNBUSDT: {
    name: "BNB",
    symbol: "BNB",
    category: "Exchange Token",
    description: "Binance ekosisteminde kullanılan yerel token."
  },
  SOLUSDT: {
    name: "Solana",
    symbol: "SOL",
    category: "Layer 1",
    description: "Yüksek hızlı ve düşük maliyetli blockchain ağı."
  },
  ADAUSDT: {
    name: "Cardano",
    symbol: "ADA",
    category: "Layer 1",
    description: "Akademik temelli geliştirilmiş blockchain platformu."
  },
  XRPUSDT: {
    name: "XRP",
    symbol: "XRP",
    category: "Payment",
    description: "Hızlı ve düşük maliyetli uluslararası ödeme ağı."
  },
  DOTUSDT: {
    name: "Polkadot",
    symbol: "DOT",
    category: "Interoperability",
    description: "Farklı blockchain’leri birbirine bağlayan ağ."
  },
  LINKUSDT: {
    name: "Chainlink",
    symbol: "LINK",
    category: "Oracle",
    description: "Akıllı kontratlara gerçek dünya verisi sağlayan oracle ağı."
  },
  AVAXUSDT: {
    name: "Avalanche",
    symbol: "AVAX",
    category: "Layer 1",
    description: "Hızlı ve ölçeklenebilir blockchain platformu."
  },
  LTCUSDT: {
    name: "Litecoin",
    symbol: "LTC",
    category: "Payment",
    description: "Bitcoin’e alternatif olarak geliştirilmiş hızlı ödeme ağı."
  },
  TRXUSDT: {
    name: "TRON",
    symbol: "TRX",
    category: "Content",
    description: "İçerik paylaşımı ve eğlence odaklı blockchain platformu."
  },
  NEARUSDT: {
    name: "NEAR Protocol",
    symbol: "NEAR",
    category: "Layer 1",
    description: "Kullanıcı dostu ve ölçeklenebilir blockchain ağı."
  },
  XLMUSDT: {
    name: "Stellar",
    symbol: "XLM",
    category: "Payment",
    description: "Sınır ötesi hızlı para transferleri için geliştirilmiştir."
  },
  ATOMUSDT: {
    name: "Cosmos",
    symbol: "ATOM",
    category: "Interoperability",
    description: "Blockchain’ler arası iletişimi sağlayan ekosistem."
  },
  ARBUSDT: {
    name: "Arbitrum",
    symbol: "ARB",
    category: "Layer 2",
    description: "Ethereum için ölçeklendirme çözümü (Layer 2)."
  },
  OPUSDT: {
    name: "Optimism",
    symbol: "OP",
    category: "Layer 2",
    description: "Ethereum ağını hızlandıran Layer 2 çözümü."
  }
};

export type FxMeta = {
  baseName: string;
  quoteName: string;
  baseCountry: string;
  quoteCountry: string;
  description: string;
};

export const fxMeta: Record<string, FxMeta> = {
  USDTRY: {
    baseName: "ABD Doları",
    quoteName: "Türk Lirası",
    baseCountry: "ABD",
    quoteCountry: "Türkiye",
    description: "Doların Türk Lirası karşısındaki değerini gösterir."
  },
  EURTRY: {
    baseName: "Euro",
    quoteName: "Türk Lirası",
    baseCountry: "Euro Bölgesi",
    quoteCountry: "Türkiye",
    description: "Euro’nun Türk Lirası karşısındaki değerini gösterir."
  },
  GBPTRY: {
    baseName: "İngiliz Sterlini",
    quoteName: "Türk Lirası",
    baseCountry: "İngiltere",
    quoteCountry: "Türkiye",
    description: "Sterlinin Türk Lirası karşısındaki değerini gösterir."
  }
};

export type EtfMeta = {
  name: string;
  category: string;
  provider: string;
  description: string;
};

export const etfMeta: Record<string, EtfMeta> = {
  EEM: {
    name: "iShares MSCI Emerging Markets ETF",
    category: "Gelişen Piyasalar",
    provider: "BlackRock",
    description:
      "Gelişmekte olan ülkelerin hisse senedi piyasalarına yatırım yapar."
  },
  QQQ: {
    name: "Invesco QQQ Trust",
    category: "NASDAQ 100",
    provider: "Invesco",
    description:
      "NASDAQ 100 endeksini takip eder, teknoloji ağırlıklıdır."
  },
  SPY: {
    name: "SPDR S&P 500 ETF",
    category: "S&P 500",
    provider: "State Street",
    description:
      "ABD’nin en büyük 500 şirketini temsil eden S&P 500 endeksini takip eder."
  },
  VTI: {
    name: "Vanguard Total Stock Market ETF",
    category: "Total Market",
    provider: "Vanguard",
    description:
      "ABD hisse senedi piyasasının tamamını kapsar."
  },
  VOO: {
    name: "Vanguard S&P 500 ETF",
    category: "S&P 500",
    provider: "Vanguard",
    description:
      "S&P 500 endeksini düşük maliyetle takip eder."
  },
  IWM: {
    name: "iShares Russell 2000 ETF",
    category: "Small Cap",
    provider: "BlackRock",
    description:
      "ABD küçük ölçekli şirketlerini temsil eder."
  },
  GLD: {
    name: "SPDR Gold Shares",
    category: "Altın",
    provider: "State Street",
    description:
      "Fiziksel altın fiyatını takip eden ETF."
  }
};

export type BondMeta = {
  issuer: string;
  type: string;
  category: string;
  risk: string;
  description: string;
};

export function getBondMeta(days: number): BondMeta {
  if (days < 90) {
    return {
      issuer: "Türkiye Cumhuriyeti",
      type: "Devlet Tahvili",
      category: "Kısa Vadeli",
      risk: "Düşük",
      description:
        "Kısa vadeli devlet tahvili. Düşük riskli ve düşük getirili yatırım aracıdır."
    };
  }

  if (days < 365) {
    return {
      issuer: "Türkiye Cumhuriyeti",
      type: "Devlet Tahvili",
      category: "Orta Vadeli",
      risk: "Orta",
      description:
        "Orta vadeli devlet tahvili. Dengeli risk ve getiri profiline sahiptir."
    };
  }

  return {
    issuer: "Türkiye Cumhuriyeti",
    type: "Devlet Tahvili",
    category: "Uzun Vadeli",
    risk: "Orta-Yüksek",
    description:
      "Uzun vadeli devlet tahvili. Faiz değişimlerine karşı daha hassastır."
  };
}

