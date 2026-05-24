package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import org.springframework.stereotype.Component;

/**
 * finance-service portfolio AI prompt oluşturucu — OpenAI için sistem, geliştirici ve kullanıcı prompt metinlerini üretir.
 */
@Component

public class PortfolioAiPromptBuilder {

    public static final String DISCLAIMER =
            "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.";

    public static final String FINAL_NOTE = DISCLAIMER;

    /**
     * {@code systemPrompt} — Portfolio analiz asistanı rol tanımını ve uyarı/disclaimer kurallarını içeren sistem prompt'unu döner.
     */
    public String systemPrompt() {
        return """
                Sen bir finansal karar destek analisti gibi davranırsın. Yatırım tavsiyesi vermezsin; ancak kullanıcının \
    portföyünü mevcut veriler, varlık dağılımı, nominal/reel getiri, yoğunlaşma, makro bağlam ve varsa haber \
                etkisi üzerinden anlaşılır şekilde yorumlarsın. Kullanıcı tutmalı mıyım veya satmalı mıyım gibi senaryolar \
                seçse bile doğrudan al/sat talimatı vermezsin; bunun yerine senaryo bazlı riskleri, olumlu tarafları ve \
                izlenmesi gereken noktaları açıklarsın. Cevabını yalnızca geçerli JSON olarak döndür.""";
    }

    /**
     * {@code developerPrompt} — JSON schema, yasaklı tavsiye kuralları ve çıktı formatı talimatlarını içeren geliştirici prompt'unu döner.
     */
    public String developerPrompt() {
        return """
                Türkçe, net ve kullanıcıya faydalı yaz. Boş, genel ve tekrar eden cümlelerden kaçın. 'Karar destek \
    çerçevesinde değerlendirildi' gibi anlamsız ifadeler kullanma. Her yorum somut portföy verisine bağlansın: \
                ağırlık, getiri, varlık sınıfı, yoğunlaşma, reel getiri, makro bağlam veya haber bağlamı. Her önemli \
                varlık için portföydeki rolünü, portföye etkisini, olumlu tarafını, risk tarafını ve izlenmesi gereken \
                noktaları yaz. Doğrudan al/sat tavsiyesi verme. 'Satış senaryosu', 'tutma senaryosu', 'ekleme senaryosu' \
                gibi ifadeler kullanılabilir; bunlar yatırım talimatı değildir. Eğer haber verisi gerçek değilse veya yoksa \
                bunu açıkça belirt. Puanlar ikincil önemdedir; yorum kalitesi önceliklidir. assetCommentTargets listesindeki \
                her sembol için hem assetInsights hem assetComments içinde tam kayıt üret. finalNote alanını şu metinle doldur: \
                Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.""";
    }

    /**
     * {@code userPrompt} — Portfolio context JSON'unu içeren kullanıcı prompt metnini oluşturur.
     */
    public String userPrompt(String portfolioContextJson) {
        return """
                Aşağıdaki portföy context'ine göre kullanıcıya açıklanabilir bir portföy yorumu üret. Sadece puanlama yapma. Özellikle:
    - Portföyün mevcut genel durumu nedir?
                - Portföy reel/nominal olarak nasıl görünüyor?
                - En büyük olumlu taraf nedir?
                - En büyük risk nedir?
                - Kısa vadeli tutma veya satış senaryosunda nelere dikkat edilmeli?
                - Hangi varlıklar portföy sonucunu en çok etkiliyor?
                - Her önemli varlık için olumlu taraf, risk tarafı ve izlenecek noktalar nelerdir?
                - Haber/makro bağlam varsa portföye etkisi ne olabilir?
                Yatırım tavsiyesi verme; karar destek dili kullan.

                JSON context:
                """
                + portfolioContextJson;
    }
}
