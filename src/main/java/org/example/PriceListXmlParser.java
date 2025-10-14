package org.example;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

public class PriceListXmlParser {

    private static final String PRICE_LIST = "src/main/resources/PriceList.yml";

    public static List<Map<String, Object>> loadOffers() {
        List<Map<String, Object>> offers = new ArrayList<>();

        try {
            File xmlFile = new File(PRICE_LIST);
            if (!xmlFile.exists()) {
                System.err.println("❌ Файл " + PRICE_LIST + " не знайдено!");
                return offers;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Додайте ці рядки для вимкнення завантаження DTD
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Додайте наступний рядок, щоб уникнути помилок, якщо в XML-файлі є оголошення DTD
            builder.setEntityResolver((publicId, systemId) -> {
                System.out.println("⚠️ Пропущено завантаження DTD: " + systemId);
                return new org.xml.sax.InputSource(new java.io.StringReader(""));
            });

            // Використовуйте InputStreamReader для явного вказання кодування
            try (InputStreamReader isr = new InputStreamReader(new FileInputStream(xmlFile), "Windows-1251")) {
                Document doc = builder.parse(new org.xml.sax.InputSource(isr));
                doc.getDocumentElement().normalize();

                NodeList offerNodes = doc.getElementsByTagName("offer");
                for (int i = 0; i < offerNodes.getLength(); i++) {
                    Node node = offerNodes.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element offerEl = (Element) node;

                        Map<String, Object> offer = new HashMap<>();

                        // Назва товару
                        Node nameNode = offerEl.getElementsByTagName("name").item(0);
                        if (nameNode != null) {
                            offer.put("name", nameNode.getTextContent().trim());
                        }

                        // Ціна
                        Node priceNode = offerEl.getElementsByTagName("price").item(0);
                        if (priceNode != null) {
                            offer.put("price", priceNode.getTextContent().trim());
                        }

                        offers.add(offer);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return offers;
    }
}