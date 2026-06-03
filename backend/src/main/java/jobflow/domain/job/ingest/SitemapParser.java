package jobflow.domain.job.ingest;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
public class SitemapParser {

    public ParsedSitemap parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Sitemap XML must not be blank");
        }

        Document document = parseDocument(xml);
        Element root = document.getDocumentElement();
        String rootName = localName(root);

        if ("urlset".equals(rootName)) {
            return new ParsedSitemap(SitemapType.URL_SET, parseEntries(root, "url"));
        }

        if ("sitemapindex".equals(rootName)) {
            return new ParsedSitemap(SitemapType.SITEMAP_INDEX, parseEntries(root, "sitemap"));
        }

        throw new IllegalArgumentException("Unsupported sitemap root: " + rootName);
    }

    private Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse sitemap XML", exception);
        }
    }

    private List<SitemapEntry> parseEntries(Element root, String entryName) {
        NodeList nodes = elementsByName(root, entryName);
        List<SitemapEntry> entries = new ArrayList<>();

        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            String loc = childText(element, "loc");

            if (loc == null || loc.isBlank()) {
                continue;
            }

            entries.add(new SitemapEntry(
                    loc.trim(),
                    parseLastModified(childText(element, "lastmod"))
            ));
        }

        return List.copyOf(entries);
    }

    private NodeList elementsByName(Element element, String name) {
        NodeList nodes = element.getElementsByTagNameNS("*", name);

        if (nodes.getLength() > 0) {
            return nodes;
        }

        return element.getElementsByTagName(name);
    }

    private String childText(Element element, String childName) {
        NodeList nodes = elementsByName(element, childName);

        if (nodes.getLength() == 0) {
            return null;
        }

        return nodes.item(0).getTextContent();
    }

    private LocalDateTime parseLastModified(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String localName(Element element) {
        if (element.getLocalName() != null) {
            return element.getLocalName();
        }

        return element.getNodeName();
    }
}
