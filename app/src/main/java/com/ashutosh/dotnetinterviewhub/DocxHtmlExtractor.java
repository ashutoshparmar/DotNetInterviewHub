package com.ashutosh.dotnetinterviewhub;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline DOCX-to-HTML renderer for Knowledge Hub. */
public final class DocxHtmlExtractor {
    private static final String DOCUMENT_PART = "word/document.xml";
    private DocxHtmlExtractor() {}

    public static String extract(InputStream source) throws Exception {
        RenderContext context = new RenderContext(readEntries(source));
        byte[] documentXml = context.entries.get(DOCUMENT_PART);
        if (documentXml == null)
            throw new IllegalArgumentException("The selected file is not a readable DOCX document.");
        Document document = DocxSecurity.parseXml(documentXml);
        StringBuilder content = new StringBuilder();
        appendReferencedParts(content, document, "headerReference", "doc-header", context);
        appendContainer(content, firstBySuffix(document, "body"), DOCUMENT_PART, context);
        appendReferencedParts(content, document, "footerReference", "doc-footer", context);
        return htmlDocument(content.toString());
    }

    private static void appendReferencedParts(StringBuilder output, Document document, String referenceName,
                                              String cssClass, RenderContext context) throws Exception {
        Set<String> rendered = new HashSet<>();
        for (Node reference : descendants(document, referenceName)) {
            Relationship relationship = context.relationship(DOCUMENT_PART, attribute(reference, "id"));
            if (relationship == null) continue;
            String part = resolvePart(DOCUMENT_PART, relationship.target);
            if (part.isEmpty() || !rendered.add(part) || !context.entries.containsKey(part)) continue;
            Document related = DocxSecurity.parseXml(context.entries.get(part));
            output.append("<section class=\"").append(cssClass).append("\">");
            appendContainer(output, related.getDocumentElement(), part, context);
            output.append("</section>\n");
        }
    }

    private static void appendContainer(StringBuilder output, Node container, String part,
                                        RenderContext context) {
        if (container == null) return;
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("p".equals(name)) appendParagraph(output, child, part, context);
            else if ("tbl".equals(name)) appendTable(output, child, part, context);
        }
    }

    private static void appendParagraph(StringBuilder output, Node paragraph, String part,
                                        RenderContext context) {
        if (!hasRenderableContent(paragraph)) return;
        String style = paragraphStyle(paragraph).toLowerCase(Locale.ROOT);
        String tag = "p";
        if (style.contains("title") || style.contains("heading1")) tag = "h1";
        else if (style.contains("heading2")) tag = "h2";
        else if (style.contains("heading3")) tag = "h3";
        else if (style.contains("codeblock") || style.equals("code")) tag = "pre";

        NumberMarker marker = context.numbering.marker(paragraph);
        if (marker == null && style.contains("listbullet")) marker = new NumberMarker("•", 0);
        String css = paragraphCss(paragraph);
        if (marker != null) css += "padding-left:" + (18 + marker.level * 18) + "px;text-indent:-18px;";
        if (hasPageBreakBefore(paragraph)) css += "break-before:page;";

        output.append('<').append(tag);
        if (!css.isEmpty()) output.append(" style=\"").append(css).append("\"");
        output.append('>');
        if (marker != null)
            output.append("<span class=\"list-marker\">").append(escape(marker.text)).append("&emsp;</span>");
        appendInlineChildren(output, paragraph, part, context);
        output.append("</").append(tag).append(">\n");
    }

    private static void appendTable(StringBuilder output, Node table, String part,
                                    RenderContext context) {
        List<List<CellInfo>> rows = new ArrayList<>();
        for (Node row : directChildren(table, "tr")) rows.add(rowCells(row));
        output.append("<div class=\"table-wrap\"><table>");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            output.append("<tr>");
            for (CellInfo cell : rows.get(rowIndex)) {
                if ("continue".equals(cell.verticalMerge)) continue;
                int rowSpan = "restart".equals(cell.verticalMerge)
                        ? verticalSpan(rows, rowIndex, cell.gridStart) : 1;
                String css = cellCss(cell.node);
                output.append("<td");
                if (cell.span > 1) output.append(" colspan=\"").append(cell.span).append("\"");
                if (rowSpan > 1) output.append(" rowspan=\"").append(rowSpan).append("\"");
                if (!css.isEmpty()) output.append(" style=\"").append(css).append("\"");
                output.append('>');
                boolean wrote = false;
                for (Node child : directChildren(cell.node, null)) {
                    if ("p".equals(localName(child))) {
                        if (!hasRenderableContent(child)) continue;
                        if (wrote) output.append("<br>");
                        NumberMarker marker = context.numbering.marker(child);
                        if (marker != null)
                            output.append("<span class=\"list-marker\">").append(escape(marker.text)).append("&emsp;</span>");
                        appendInlineChildren(output, child, part, context);
                        wrote = true;
                    } else if ("tbl".equals(localName(child))) {
                        appendTable(output, child, part, context);
                        wrote = true;
                    }
                }
                output.append("</td>");
            }
            output.append("</tr>");
        }
        output.append("</table></div>\n");
    }

    private static List<CellInfo> rowCells(Node row) {
        List<CellInfo> result = new ArrayList<>();
        int grid = 0;
        for (Node cell : directChildren(row, "tc")) {
            Node properties = firstDirectChild(cell, "tcPr");
            int span = Math.max(1, positiveInt(descendantAttribute(properties, "gridSpan", "val"), 1));
            Node merge = firstDirectChild(properties, "vMerge");
            String verticalMerge = "none";
            if (merge != null) {
                String value = attribute(merge, "val");
                verticalMerge = "restart".equalsIgnoreCase(value) ? "restart" : "continue";
            }
            result.add(new CellInfo(cell, grid, span, verticalMerge));
            grid += span;
        }
        return result;
    }

    private static int verticalSpan(List<List<CellInfo>> rows, int startRow, int gridStart) {
        int span = 1;
        for (int row = startRow + 1; row < rows.size(); row++) {
            CellInfo candidate = cellAt(rows.get(row), gridStart);
            if (candidate == null || !"continue".equals(candidate.verticalMerge)) break;
            span++;
        }
        return span;
    }

    private static CellInfo cellAt(List<CellInfo> cells, int gridStart) {
        for (CellInfo cell : cells) if (cell.gridStart == gridStart) return cell;
        return null;
    }

    private static void appendInlineChildren(StringBuilder output, Node parent, String part,
                                             RenderContext context) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String name = localName(child);
            if ("r".equals(name)) appendRun(output, child, part, context);
            else if ("hyperlink".equals(name)) {
                Relationship relationship = context.relationship(part, attribute(child, "id"));
                if (relationship != null && isSafeLink(relationship.target)) {
                    output.append("<a href=\"").append(escapeAttribute(relationship.target)).append("\">");
                    appendInlineChildren(output, child, part, context);
                    output.append("</a>");
                } else appendInlineChildren(output, child, part, context);
            } else if ("fldSimple".equals(name) || "sdt".equals(name)) appendInlineChildren(output, child, part, context);
        }
    }

    private static void appendRun(StringBuilder output, Node run, String part, RenderContext context) {
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
            if ("t".equals(name) || "delText".equals(name)) output.append(escape(child.getTextContent()));
            else if ("tab".equals(name)) output.append("&emsp;");
            else if ("br".equals(name)) {
                if ("page".equalsIgnoreCase(attribute(child, "type"))) output.append("<span class=\"page-break\"></span>");
                else output.append("<br>");
            } else if ("lastRenderedPageBreak".equals(name)) output.append("<span class=\"page-break\"></span>");
            else if ("drawing".equals(name) || "pict".equals(name)) appendImage(output, child, part, context);
        }
        if (underline) output.append("</u>");
        if (italic) output.append("</em>");
        if (bold) output.append("</strong>");
        if (!css.isEmpty()) output.append("</span>");
    }

    private static void appendImage(StringBuilder output, Node drawing, String part, RenderContext context) {
        Node imageNode = firstDescendant(drawing, "blip");
        if (imageNode == null) imageNode = firstDescendant(drawing, "imagedata");
        if (imageNode == null) return;
        String id = attribute(imageNode, "embed");
        if (id.isEmpty()) id = attribute(imageNode, "id");
        Relationship relationship = context.relationship(part, id);
        if (relationship == null) return;
        String imagePart = resolvePart(part, relationship.target);
        byte[] image = context.entries.get(imagePart);
        String mime = imageMime(imagePart);
        if (image == null || mime.isEmpty()) return;
        Node description = firstDescendant(drawing, "docPr");
        String alt = description == null ? "Document image" : attribute(description, "descr");
        if (alt.isEmpty() && description != null) alt = attribute(description, "name");
        if (alt.isEmpty()) alt = "Document image";
        output.append("<img alt=\"").append(escapeAttribute(alt)).append("\" src=\"data:")
                .append(mime).append(";base64,").append(base64(image)).append("\">");
    }

    private static String paragraphCss(Node paragraph) {
        Node properties = firstDirectChild(paragraph, "pPr");
        if (properties == null) return "";
        StringBuilder css = new StringBuilder();
        String fill = descendantAttribute(properties, "shd", "fill");
        if (validColour(fill)) css.append("background:#").append(fill).append(';');
        Node left = firstDirectChild(firstDirectChild(properties, "pBdr"), "left");
        if (left != null) {
            String colour = attribute(left, "color");
            if (!validColour(colour)) colour = "2E74B5";
            css.append("border-left:4px solid #").append(colour).append(";padding:10px 12px;");
        }
        String align = descendantAttribute(properties, "jc", "val");
        if ("center".equals(align) || "right".equals(align) || "justify".equals(align))
            css.append("text-align:").append(align).append(';');
        String before = descendantAttribute(properties, "spacing", "before");
        String after = descendantAttribute(properties, "spacing", "after");
        if (!before.isEmpty()) css.append("margin-top:").append(twipsToPoints(before)).append("pt;");
        if (!after.isEmpty()) css.append("margin-bottom:").append(twipsToPoints(after)).append("pt;");
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
        if (hasProperty(properties, "strike")) css.append("text-decoration:line-through;");
        return css.toString();
    }

    private static String cellCss(Node cell) {
        Node properties = firstDirectChild(cell, "tcPr");
        StringBuilder css = new StringBuilder();
        String fill = descendantAttribute(properties, "shd", "fill");
        if (validColour(fill)) css.append("background:#").append(fill).append(';');
        String align = descendantAttribute(properties, "vAlign", "val");
        if ("center".equals(align)) css.append("vertical-align:middle;");
        else if ("bottom".equals(align)) css.append("vertical-align:bottom;");
        return css.toString();
    }

    private static boolean hasPageBreakBefore(Node paragraph) {
        return firstDirectChild(firstDirectChild(paragraph, "pPr"), "pageBreakBefore") != null;
    }

    private static String paragraphStyle(Node paragraph) {
        Node style = firstDirectChild(firstDirectChild(paragraph, "pPr"), "pStyle");
        return style == null ? "" : attribute(style, "val");
    }

    private static boolean hasProperty(Node properties, String name) {
        Node value = firstDirectChild(properties, name);
        if (value == null) return false;
        String enabled = attribute(value, "val");
        return enabled.isEmpty() || !("0".equals(enabled) || "false".equalsIgnoreCase(enabled));
    }

    private static boolean hasRenderableContent(Node node) {
        return !plainText(node).trim().isEmpty() || firstDescendant(node, "drawing") != null
                || firstDescendant(node, "pict") != null || firstDescendant(node, "br") != null;
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
        int count = 0;
        int total = 0;
        int mediaTotal = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > DocxSecurity.MAX_ZIP_ENTRIES)
                    throw new IllegalArgumentException("The DOCX contains too many internal files.");
                String name = normalizePart(entry.getName());
                if (!wantedPart(name)) continue;
                int limit = name.startsWith("word/media/")
                        ? DocxSecurity.MAX_MEDIA_ENTRY_BYTES : DocxSecurity.MAX_XML_ENTRY_BYTES;
                if (entry.getSize() > limit)
                    throw new IllegalArgumentException("The DOCX contains an oversized internal file.");
                byte[] value = DocxSecurity.readLimited(zip, limit,
                        "The DOCX contains an oversized internal file.");
                total += value.length;
                if (total > DocxSecurity.MAX_TOTAL_EXTRACTED_BYTES)
                    throw new IllegalArgumentException("The expanded DOCX is too large to import safely.");
                if (name.startsWith("word/media/")) {
                    mediaTotal += value.length;
                    if (mediaTotal > DocxSecurity.MAX_TOTAL_MEDIA_BYTES)
                        throw new IllegalArgumentException("The DOCX contains more than 12 MB of embedded images.");
                }
                entries.put(name, value);
            }
        }
        return entries;
    }

    private static boolean wantedPart(String name) {
        return DOCUMENT_PART.equals(name) || "word/numbering.xml".equals(name)
                || name.matches("word/(header|footer)[^/]*\\.xml")
                || name.matches("word/_rels/(document|header[^/]*|footer[^/]*)\\.xml\\.rels")
                || name.startsWith("word/media/");
    }

    private static String relationshipPart(String part) {
        int slash = part.lastIndexOf('/');
        String directory = slash < 0 ? "" : part.substring(0, slash + 1);
        String file = slash < 0 ? part : part.substring(slash + 1);
        return directory + "_rels/" + file + ".rels";
    }

    private static String resolvePart(String sourcePart, String target) {
        if (target == null || target.isEmpty() || isSafeLink(target)) return "";
        String candidate;
        if (target.startsWith("/")) candidate = target.substring(1);
        else {
            int slash = sourcePart.lastIndexOf('/');
            candidate = (slash < 0 ? "" : sourcePart.substring(0, slash + 1)) + target;
        }
        return normalizePart(candidate);
    }

    private static String normalizePart(String value) {
        List<String> segments = new ArrayList<>();
        for (String segment : value.replace('\\', '/').split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (segments.isEmpty()) return "";
                segments.remove(segments.size() - 1);
            } else segments.add(segment);
        }
        return join(segments, "/");
    }

    private static Map<String, Relationship> parseRelationships(byte[] xml) throws Exception {
        Map<String, Relationship> result = new HashMap<>();
        if (xml == null) return result;
        Document document = DocxSecurity.parseXml(xml);
        for (Node node : descendants(document, "Relationship")) {
            String id = attribute(node, "Id");
            String target = attribute(node, "Target");
            if (!id.isEmpty() && !target.isEmpty()) result.put(id, new Relationship(target));
        }
        return result;
    }

    private static Node firstBySuffix(Document document, String suffix) {
        for (Node node : descendants(document, suffix)) return node;
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

    private static Node firstDescendant(Node parent, String name) {
        for (Node node : descendants(parent, name)) return node;
        return null;
    }

    private static List<Node> directChildren(Node parent, String name) {
        List<Node> result = new ArrayList<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && (name == null || name.equals(localName(child))))
                result.add(child);
        }
        return result;
    }

    private static List<Node> descendants(Node parent, String name) {
        List<Node> result = new ArrayList<>();
        collectDescendants(parent, name, result);
        return result;
    }

    private static void collectDescendants(Node parent, String name, List<Node> result) {
        if (parent == null) return;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            if (name.equals(localName(child))) result.add(child);
            collectDescendants(child, name, result);
        }
    }

    private static String descendantAttribute(Node parent, String elementName, String attributeName) {
        Node node = firstDescendant(parent, elementName);
        return node == null ? "" : attribute(node, attributeName);
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

    private static String htmlDocument(String body) {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">" +
                "<style>body{font-family:Calibri,Arial,sans-serif;color:#111827;background:#fff;margin:0;padding:20px;" +
                "font-size:16px;line-height:1.45}h1{color:#1f4e78;font-size:25px;margin:24px 0 12px}" +
                "h2{color:#2e74b5;font-size:21px;margin:20px 0 10px}h3{color:#1f4e78;font-size:18px;margin:16px 0 8px}" +
                "p{margin:0 0 10px;white-space:pre-wrap}pre{font-family:Consolas,monospace;background:#f2f4f7;" +
                "padding:12px;overflow-x:auto;white-space:pre-wrap;margin:8px 0 12px}.table-wrap{overflow-x:auto;margin:10px 0 16px}" +
                "table{border-collapse:collapse;width:100%;min-width:600px}td{border:1px solid #333;padding:8px;vertical-align:top}" +
                "img{display:block;max-width:100%;height:auto;margin:10px auto}.doc-header,.doc-footer{color:#59636f;font-size:12px}" +
                ".doc-header{border-bottom:1px solid #d7dde5;margin-bottom:16px}.doc-footer{border-top:1px solid #d7dde5;margin-top:18px}" +
                ".page-break{display:block;border-top:1px dashed #aab4c0;margin:22px 0;break-after:page}.list-marker{font-weight:600}" +
                "a{color:#0563c1}strong{font-weight:700}@media print{.page-break{border:0;page-break-after:always}}" +
                "</style></head><body>" + body + "</body></html>";
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
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }

    private static String imageMime(String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "";
    }

    private static int positiveInt(String value, int fallback) {
        try { int result = Integer.parseInt(value); return result >= 0 ? result : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static double twipsToPoints(String value) {
        try { return Double.parseDouble(value) / 20d; }
        catch (Exception ignored) { return 0; }
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static String base64(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder result = new StringBuilder((bytes.length + 2) / 3 * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int a = bytes[i] & 255;
            int b = i + 1 < bytes.length ? bytes[i + 1] & 255 : 0;
            int c = i + 2 < bytes.length ? bytes[i + 2] & 255 : 0;
            result.append(alphabet[a >>> 2]);
            result.append(alphabet[((a & 3) << 4) | (b >>> 4)]);
            result.append(i + 1 < bytes.length ? alphabet[((b & 15) << 2) | (c >>> 6)] : '=');
            result.append(i + 2 < bytes.length ? alphabet[c & 63] : '=');
        }
        return result.toString();
    }

    private static String formatNumber(int value, String format) {
        if ("lowerLetter".equals(format)) return letters(value, false);
        if ("upperLetter".equals(format)) return letters(value, true);
        if ("lowerRoman".equals(format)) return roman(value).toLowerCase(Locale.ROOT);
        if ("upperRoman".equals(format)) return roman(value);
        return String.valueOf(value);
    }

    private static String letters(int value, boolean upper) {
        StringBuilder result = new StringBuilder();
        int number = Math.max(1, value);
        while (number > 0) {
            number--;
            result.insert(0, (char) ((upper ? 'A' : 'a') + number % 26));
            number /= 26;
        }
        return result.toString();
    }

    private static String roman(int value) {
        if (value <= 0 || value > 3999) return String.valueOf(value);
        int[] numbers = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] symbols = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < numbers.length; i++)
            while (value >= numbers[i]) { result.append(symbols[i]); value -= numbers[i]; }
        return result.toString();
    }

    private static final class RenderContext {
        final Map<String, byte[]> entries;
        final Map<String, Map<String, Relationship>> relationships = new HashMap<>();
        final Numbering numbering;
        RenderContext(Map<String, byte[]> entries) throws Exception {
            this.entries = entries;
            this.numbering = new Numbering(entries.get("word/numbering.xml"));
        }
        Relationship relationship(String part, String id) {
            if (id == null || id.isEmpty()) return null;
            try {
                Map<String, Relationship> values = relationships.get(part);
                if (values == null) {
                    values = parseRelationships(entries.get(relationshipPart(part)));
                    relationships.put(part, values);
                }
                return values.get(id);
            } catch (Exception ignored) { return null; }
        }
    }

    private static final class Relationship {
        final String target;
        Relationship(String target) { this.target = target; }
    }

    private static final class CellInfo {
        final Node node; final int gridStart; final int span; final String verticalMerge;
        CellInfo(Node node, int gridStart, int span, String verticalMerge) {
            this.node = node; this.gridStart = gridStart; this.span = span; this.verticalMerge = verticalMerge;
        }
    }

    private static final class NumberMarker {
        final String text; final int level;
        NumberMarker(String text, int level) { this.text = text; this.level = level; }
    }

    private static final class LevelDefinition {
        final int start; final String format; final String text;
        LevelDefinition(int start, String format, String text) {
            this.start = start; this.format = format; this.text = text;
        }
    }

    private static final class Numbering {
        final Map<String, String> abstractByNumber = new HashMap<>();
        final Map<String, Map<Integer, LevelDefinition>> levelsByAbstract = new HashMap<>();
        final Map<String, int[]> counters = new HashMap<>();

        Numbering(byte[] xml) throws Exception {
            if (xml == null) return;
            Document document = DocxSecurity.parseXml(xml);
            for (Node abstractNumber : descendants(document, "abstractNum")) {
                String id = attribute(abstractNumber, "abstractNumId");
                if (id.isEmpty()) continue;
                Map<Integer, LevelDefinition> levels = new HashMap<>();
                for (Node level : directChildren(abstractNumber, "lvl")) {
                    int index = positiveInt(attribute(level, "ilvl"), 0);
                    int start = positiveInt(descendantAttribute(level, "start", "val"), 1);
                    String format = descendantAttribute(level, "numFmt", "val");
                    String text = descendantAttribute(level, "lvlText", "val");
                    levels.put(index, new LevelDefinition(start, format, text));
                }
                levelsByAbstract.put(id, levels);
            }
            for (Node number : descendants(document, "num")) {
                String id = attribute(number, "numId");
                String abstractId = descendantAttribute(number, "abstractNumId", "val");
                if (!id.isEmpty() && !abstractId.isEmpty()) abstractByNumber.put(id, abstractId);
            }
        }

        NumberMarker marker(Node paragraph) {
            Node numberProperties = firstDirectChild(firstDirectChild(paragraph, "pPr"), "numPr");
            if (numberProperties == null) return null;
            String numberId = descendantAttribute(numberProperties, "numId", "val");
            int level = Math.min(8, positiveInt(descendantAttribute(numberProperties, "ilvl", "val"), 0));
            Map<Integer, LevelDefinition> levels = levelsByAbstract.get(abstractByNumber.get(numberId));
            LevelDefinition definition = levels == null ? null : levels.get(level);
            if (definition == null) return new NumberMarker("•", level);
            if ("bullet".equals(definition.format)) return new NumberMarker(normalizeBullet(definition.text), level);

            int[] values = counters.get(numberId);
            if (values == null) { values = new int[9]; counters.put(numberId, values); }
            if (values[level] == 0) values[level] = Math.max(1, definition.start);
            else values[level]++;
            for (int i = level + 1; i < values.length; i++) values[i] = 0;

            String label = definition.text == null || definition.text.isEmpty() ? "%" + (level + 1) + "." : definition.text;
            for (int i = 0; i < 9; i++) {
                LevelDefinition tokenDefinition = levels.get(i);
                int tokenValue = values[i] == 0 && tokenDefinition != null ? tokenDefinition.start : values[i];
                String format = tokenDefinition == null ? "decimal" : tokenDefinition.format;
                label = label.replace("%" + (i + 1), formatNumber(tokenValue, format));
            }
            return new NumberMarker(label, level);
        }

        private static String normalizeBullet(String value) {
            if (value == null || value.trim().isEmpty()) return "•";
            String clean = value.trim();
            if ("".equals(clean) || "o".equals(clean) || "".equals(clean)) return "•";
            return clean;
        }
    }
}
