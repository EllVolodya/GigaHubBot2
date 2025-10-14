package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;

public class CatalogConverter {

    public static void convert() throws Exception {
        String resourcesPath = "src/main/resources/";
        File xmlFile = new File(resourcesPath + "PriceList.yml");   // твій XML
        File yamlFile = new File(resourcesPath + "catalogs.yml");   // майбутній YAML

        // Парсимо XML як DOM
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList offers = doc.getElementsByTagName("offer");

        List<Map<String, Object>> products = new ArrayList<>();

        for (int i = 0; i < offers.getLength(); i++) {
            Node node = offers.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element offer = (Element) node;

                String name = getTagValue(offer, "name");
                String price = getTagValue(offer, "price");

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("name", name != null ? name : "Без назви");
                product.put("price", price != null ? Double.parseDouble(price.replace(",", ".")) : 0.0);

                products.add(product);
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("products", products);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);

        Yaml yaml = new Yaml(options);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(yamlFile), "UTF-8")) {
            yaml.dump(root, writer);
        }

        System.out.println("✅ catalogs.yml створено/оновлено!");
    }

    private static String getTagValue(Element element, String tag) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }
}
