package com.nurseli.marketdata.infrastructure.tcmb;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TcmbClient {

    private final DataSourcesProperties dataSourcesProperties;

    // 🔵 SADECE DÖVİZLER
    private static final List<String> SUPPORTED =
            List.of("USD", "EUR", "GBP");

    public List<TcmbRate> fetchRates() {

        RestTemplate restTemplate = new RestTemplate();
        String url = dataSourcesProperties.getTcmb().getUrl();
        String xml = restTemplate.getForObject(url, String.class);

        List<TcmbRate> result = new ArrayList<>();

        if (xml == null || xml.isBlank()) {
            return result;
        }

        try {
            Document document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xpath = XPathFactory.newInstance().newXPath();

            for (String code : SUPPORTED) {

                String buyPath =
                        "/Tarih_Date/Currency[@CurrencyCode='" + code + "']/ForexBuying";
                String sellPath =
                        "/Tarih_Date/Currency[@CurrencyCode='" + code + "']/ForexSelling";

                Node buyNode = (Node) xpath.evaluate(buyPath, document, XPathConstants.NODE);
                Node sellNode = (Node) xpath.evaluate(sellPath, document, XPathConstants.NODE);

                if (buyNode == null || sellNode == null) {
                    continue;
                }

                String buyText = buyNode.getTextContent();
                String sellText = sellNode.getTextContent();

                if (buyText == null || buyText.isBlank()
                        || sellText == null || sellText.isBlank()) {
                    continue;
                }

                BigDecimal buy = new BigDecimal(buyText.replace(",", "."));
                BigDecimal sell = new BigDecimal(sellText.replace(",", "."));

                result.add(new TcmbRate(code, buy, sell));
            }

        } catch (Exception e) {
            throw new RuntimeException("TCMB FX XML parse error", e);
        }

        return result;
    }
}