package com.ashutosh.dotnetinterviewhub;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DocxTextExtractor {
    private DocxTextExtractor() {}

    public static String extract(InputStream source) throws Exception {
        byte[] xml = null;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > DocxSecurity.MAX_ZIP_ENTRIES)
                    throw new IllegalArgumentException("The DOCX contains too many internal files.");
                if ("word/document.xml".equals(entry.getName())) {
                    xml = DocxSecurity.readLimited(zip, DocxSecurity.MAX_XML_ENTRY_BYTES,
                            "The DOCX document content is too large.");
                    break;
                }
            }
        }
        if (xml == null) throw new IllegalArgumentException("The selected file is not a readable DOCX document.");

        Document document = DocxSecurity.parseXml(xml);
        Node body = firstBySuffix(document, "body");
        if (body == null) return "";

        StringBuilder result = new StringBuilder();
        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("p".equals(name)) appendParagraph(result, child);
            else if ("tbl".equals(name)) appendTable(result, child);
        }
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static void appendParagraph(StringBuilder output, Node paragraph) {
        String text = collectText(paragraph).trim();
        if (text.isEmpty()) return;
        String style = paragraphStyle(paragraph).toLowerCase(Locale.ROOT);
        if (style.contains("title")) output.append("# ");
        else if (style.contains("heading1")) output.append("# ");
        else if (style.contains("heading2")) output.append("## ");
        else if (style.contains("heading3")) output.append("### ");
        else if (style.contains("listbullet")) output.append("• ");
        output.append(text).append("\n\n");
    }

    private static void appendTable(StringBuilder output, Node table) {
        NodeList rows = ((Element) table).getElementsByTagName("w:tr");
        for (int i = 0; i < rows.getLength(); i++) {
            Node row = rows.item(i);
            NodeList cells = ((Element) row).getElementsByTagName("w:tc");
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < cells.getLength(); j++) {
                String value = collectText(cells.item(j)).trim();
                if (!value.isEmpty()) {
                    if (line.length() > 0) line.append(" — ");
                    line.append(value);
                }
            }
            if (line.length() > 0) output.append(line).append("\n");
        }
        output.append("\n");
    }

    private static String paragraphStyle(Node paragraph) {
        if (!(paragraph instanceof Element)) return "";
        NodeList styles = ((Element) paragraph).getElementsByTagName("w:pStyle");
        if (styles.getLength() == 0) return "";
        Element style = (Element) styles.item(0);
        return style.hasAttribute("w:val") ? style.getAttribute("w:val") : style.getAttribute("val");
    }

    private static String collectText(Node node) {
        StringBuilder text = new StringBuilder();
        collect(node, text);
        return text.toString().replaceAll("[ \t]+", " ").replaceAll(" ?\n ?", "\n");
    }

    private static void collect(Node node, StringBuilder text) {
        String name = localName(node);
        if ("t".equals(name)) text.append(node.getTextContent());
        else if ("tab".equals(name)) text.append('\t');
        else if ("br".equals(name)) text.append('\n');
        else {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) collect(children.item(i), text);
        }
    }

    private static Node firstBySuffix(Document document, String suffix) {
        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (suffix.equals(localName(all.item(i)))) return all.item(i);
        }
        return null;
    }

    private static String localName(Node node) {
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    public static String readText(InputStream source) throws Exception {
        byte[] bytes = DocxSecurity.readLimited(source, DocxSecurity.MAX_INPUT_BYTES,
                "The selected text document is larger than 20 MB.");
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }
}
