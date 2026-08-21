package com.ashutosh.dotnetinterviewhub;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Converts the useful visual structure of a DOCX file to self-contained HTML.
 *
 * This is intentionally an offline renderer, not a full Microsoft Word engine.
 * It preserves the structures used by Knowledge Hub guides: headings, paragraph
 * emphasis, colours, hyperlinks, shaded callouts/code blocks, and real tables.
 */
public final class DocxHtmlExtractor {
    private DocxHtmlExtractor() {}

    public static String extract(InputStream source) throws Exception {
        Map<String, byte[]> entries = readEntries(source);
        byte[] documentXml = entries.get("word/document.xml");
        if (documentXml == null) {
            throw new IllegalArgumentException("The selected file is not a readable DOCX document.");
        }

        Document document = parse(documentXml);
        Map<String, String> relationships = parseRelationships(entries.get("word/_rels/document.xml.rels"));
        Node body = firstBySuffix(document, "body");
        if (body == null) return htmlDocument("");

        StringBuilder content = new StringBuilder();
        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("p".equals(name)) appendParagraph(content, child, relationships);
            else if ("tbl".equals(name)) appendTable(content, child, relationships);
        }
        return htmlDocument(content.toString());
    }

    private static void appendParagraph(StringBuilder output, Node paragraph,
                                        Map<String, String> relationships) {
        if (plainText(paragraph).trim().isEmpty()) return;
        String style = paragraphStyle(paragraph).toLowerCase(Locale.ROOT);
        String tag = "p";
        if (style.contains("title") || style.contains("heading1")) tag = "h1";
        else if (style.contains("heading2")) tag = "h2";
        else if (style.contains("heading3")) tag = "h3";
        else if (style.contains("codeblock") || style.equals("code")) tag = "pre";

        String css = paragraphCss(paragraph);
        output.append('<').append(tag);
        if (!css.isEmpty()) output.append(" style=\"").append(css).append("\"");
        output.append('>');
        appendInlineChildren(output, paragraph, relationships);
        output.append("</").append(tag).append(">\n");
    }

    private static void appendTable(StringBuilder output, Node table,
                                    Map<String, String> relationships) {
        output.append("<div class=\"table-wrap\"><table>");
        for (Node row : directChildren(table, "tr")) {
            output.append("<tr>");
            for (Node cell : directChildren(row, "tc")) {
                String css = cellCss(cell);
                output.append("<td");
                if (!css.isEmpty()) output.append(" style=\"").append(css).append("\"");
                output.append('>');
                boolean wrote = false;
                for (Node child : directChildren(cell, null)) {
                    if ("p".equals(localName(child))) {
                        if (plainText(child).trim().isEmpty()) continue;
                        if (wrote) output.append("<br>");
                        appendInlineChildren(output, child, relationships);
                        wrote = true;
                    } else if ("tbl".equals(localName(child))) {
                        appendTable(output, child, relationships);
                        wrote = true;
                    }
                }
                output.append("</td>");
            }
            output.append("</tr>");
        }
        output.append("</table></div>\n");
    }

    private static void appendInlineChildren(StringBuilder output, Node parent,
                                             Map<String, String> relationships) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("r".equals(name)) appendRun(output, child);
            else if ("hyperlink".equals(name)) {
                String id = attribute(child, "id");
                String target = relationships.get(id);
                if (target != null && isSafeLink(target)) {
                    output.append("<a href=\"").append(escapeAttribute(target)).append("\">");
                    appendInlineChildren(output, child, relationships);
                    output.append("</a>");
                } else appendInlineChildren(output, child, relationships);
            }
        }
    }

    private static void appendRun(StringBuilder output, Node run) {
        Node properties = firstDirectChild(run, "rPr");
        String css = runCss(properties);
        boolean bold = hasProperty(properties, "b");
        boolean italic = hasProperty(properties, "i");
        boolean underline = hasProperty(properties, "u");

        if (!css.isEmpty()) output.append("<span style=\"").append(css).append("\">");
        if (bold) output.append("<strong>");
        if (italic) output.append("<em>");
        if (underline) output.append("<u>");

        NodeList children = run.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("t".equals(name)) output.append(escape(child.getTextContent()));
            else if ("tab".equals(name)) output.append("&emsp;");
            else if ("br".equals(name)) output.append("<br>");
        }

        if (underline) output.append("</u>");
        if (italic) output.append("</em>");
        if (bold) output.append("</strong>");
        if (!css.isEmpty()) output.append("</span>");
    }

    private static String paragraphCss(Node paragraph) {
        Node properties = firstDirectChild(paragraph, "pPr");
        if (properties == null) return "";
        StringBuilder css = new StringBuilder();
        String fill = descendantAttribute(properties, "shd", "fill");
        if (validColour(fill)) css.append("background:#").append(fill).append(';');
        Node borders = firstDirectChild(properties, "pBdr");
        Node left = firstDirectChild(borders, "left");
        if (left != null) {
            String colour = attribute(left, "color");
            if (!validColour(colour)) colour = "2E74B5";
            css.append("border-left:4px solid #").append(colour).append(';');
            css.append("padding:10px 12px;");
        }
        String align = descendantAttribute(properties, "jc", "val");
        if ("center".equals(align) || "right".equals(align) || "justify".equals(align)) {
            css.append("text-align:").append(align).append(';');
        }
        return css.toString();
    }

    private static String runCss(Node properties) {
        if (properties == null) return "";
        StringBuilder css = new StringBuilder();
        String colour = descendantAttribute(properties, "color", "val");
        if (validColour(colour)) css.append("color:#").append(colour).append(';');
        String size = descendantAttribute(properties, "sz", "val");
        if (!size.isEmpty()) {
            try { css.append("font-size:").append(Double.parseDouble(size) / 2d).append("pt;"); }
            catch (NumberFormatException ignored) {}
        }
        String highlight = descendantAttribute(properties, "highlight", "val");
        if (!highlight.isEmpty()) css.append("background:").append(cssColour(highlight)).append(';');
        return css.toString();
    }

    private static String cellCss(Node cell) {
        Node properties = firstDirectChild(cell, "tcPr");
        String fill = descendantAttribute(properties, "shd", "fill");
        return validColour(fill) ? "background:#" + fill + ';' : "";
    }

    private static String paragraphStyle(Node paragraph) {
        Node properties = firstDirectChild(paragraph, "pPr");
        Node style = firstDirectChild(properties, "pStyle");
        return style == null ? "" : attribute(style, "val");
    }

    private static boolean hasProperty(Node properties, String name) {
        Node value = firstDirectChild(properties, name);
        if (value == null) return false;
        String enabled = attribute(value, "val");
        return enabled.isEmpty() || !("0".equals(enabled) || "false".equalsIgnoreCase(enabled));
    }

    private static String plainText(Node node) {
        StringBuilder result = new StringBuilder();
        collectPlainText(node, result);
        return result.toString();
    }

    private static void collectPlainText(Node node, StringBuilder result) {
        String name = localName(node);
        if ("t".equals(name)) result.append(node.getTextContent());
        else if ("tab".equals(name)) result.append('\t');
        else if ("br".equals(name)) result.append('\n');
        else {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) collectPlainText(children.item(i), result);
        }
    }

    private static Map<String, byte[]> readEntries(InputStream source) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/document.xml".equals(name) || "word/_rels/document.xml.rels".equals(name)) {
                    entries.put(name, readEntry(zip));
                }
            }
        }
        return entries;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
        catch (Exception ignored) {}
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static Map<String, String> parseRelationships(byte[] xml) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (xml == null) return result;
        Document document = parse(xml);
        NodeList nodes = document.getElementsByTagName("Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String id = attribute(node, "Id");
            String target = attribute(node, "Target");
            if (!id.isEmpty() && !target.isEmpty()) result.put(id, target);
        }
        return result;
    }

    private static Node firstBySuffix(Document document, String suffix) {
        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (suffix.equals(localName(all.item(i)))) return all.item(i);
        }
        return null;
    }

    private static Node firstDirectChild(Node parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(localName(child))) return child;
        }
        return null;
    }

    private static java.util.List<Node> directChildren(Node parent, String name) {
        java.util.List<Node> result = new java.util.ArrayList<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && (name == null || name.equals(localName(child)))) {
                result.add(child);
            }
        }
        return result;
    }

    private static String descendantAttribute(Node parent, String elementName, String attributeName) {
        if (!(parent instanceof Element)) return "";
        NodeList nodes = ((Element) parent).getElementsByTagName("w:" + elementName);
        if (nodes.getLength() == 0) nodes = ((Element) parent).getElementsByTagName(elementName);
        return nodes.getLength() == 0 ? "" : attribute(nodes.item(0), attributeName);
    }

    private static String attribute(Node node, String name) {
        if (!(node instanceof Element)) return "";
        Element element = (Element) node;
        if (element.hasAttribute(name)) return element.getAttribute(name);
        if (element.hasAttribute("w:" + name)) return element.getAttribute("w:" + name);
        if (element.hasAttribute("r:" + name)) return element.getAttribute("r:" + name);
        return "";
    }

    private static String localName(Node node) {
        if (node == null) return "";
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static byte[] readEntry(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static String htmlDocument(String body) {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>body{font-family:Calibri,Arial,sans-serif;color:#111827;background:#fff;margin:0;padding:20px;" +
                "font-size:16px;line-height:1.45}h1{color:#1f4e78;font-size:25px;margin:24px 0 12px}" +
                "h2{color:#2e74b5;font-size:21px;margin:20px 0 10px}h3{color:#1f4e78;font-size:18px;margin:16px 0 8px}" +
                "p{margin:0 0 10px;white-space:pre-wrap}pre{font-family:Consolas,monospace;background:#f2f4f7;" +
                "padding:12px;overflow-x:auto;white-space:pre-wrap;margin:8px 0 12px}.table-wrap{overflow-x:auto;margin:10px 0 16px}" +
                "table{border-collapse:collapse;width:100%;min-width:600px}td{border:1px solid #333;padding:8px;vertical-align:top}" +
                "a{color:#0563c1}strong{font-weight:700}</style></head><body>" + body + "</body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    private static boolean validColour(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{6}") && !"auto".equalsIgnoreCase(value);
    }

    private static String cssColour(String value) {
        if (validColour(value)) return "#" + value;
        if (value == null || value.isEmpty()) return "transparent";
        return value.replaceAll("[^a-zA-Z]", "");
    }

    private static boolean isSafeLink(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }
}
