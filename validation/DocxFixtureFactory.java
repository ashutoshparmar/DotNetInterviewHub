import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a small DOCX containing every compatibility structure tested in CI. */
public final class DocxFixtureFactory {
    private DocxFixtureFactory() {}

    public static byte[] create() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "word/document.xml", document());
            add(zip, "word/_rels/document.xml.rels", relationships());
            add(zip, "word/numbering.xml", numbering());
            add(zip, "word/header1.xml", part("hdr", "Compatibility Header"));
            add(zip, "word/footer1.xml", part("ftr", "Compatibility Footer"));
            add(zip, "word/media/test.png", Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
        }
        return bytes.toByteArray();
    }

    public static byte[] createWithDoctype() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "word/document.xml", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY bad SYSTEM \"file:///etc/passwd\">]>" +
                    "<w:document xmlns:w=\"urn:test\"><w:body><w:p><w:r><w:t>&bad;</w:t></w:r></w:p></w:body></w:document>");
        }
        return bytes.toByteArray();
    }

    private static String document() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<w:document xmlns:w=\"urn:w\" xmlns:r=\"urn:r\" xmlns:a=\"urn:a\" xmlns:wp=\"urn:wp\">" +
                "<w:body>" +
                "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val=\"1F4E78\"/></w:rPr><w:t>Compatibility Guide</w:t></w:r></w:p>" +
                numbered("First numbered item") + numbered("Second numbered item") +
                "<w:tbl>" +
                "<w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"2\"/></w:tcPr><w:p><w:r><w:t>Wide cell</w:t></w:r></w:p></w:tc>" +
                "<w:tc><w:tcPr><w:vMerge w:val=\"restart\"/></w:tcPr><w:p><w:r><w:t>Tall cell</w:t></w:r></w:p></w:tc></w:tr>" +
                "<w:tr><w:tc><w:p><w:r><w:t>Left</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Right</w:t></w:r></w:p></w:tc>" +
                "<w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc></w:tr></w:tbl>" +
                "<w:p><w:r><w:drawing><wp:docPr name=\"Test image\" descr=\"Compatibility image\"/>" +
                "<a:blip r:embed=\"rImg\"/></w:drawing></w:r></w:p>" +
                "<w:p><w:r><w:t>Before page break</w:t><w:br w:type=\"page\"/><w:t>After page break</w:t></w:r></w:p>" +
                "<w:sectPr><w:headerReference r:id=\"rHeader\"/><w:footerReference r:id=\"rFooter\"/></w:sectPr>" +
                "</w:body></w:document>";
    }

    private static String numbered(String text) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>" +
                "<w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    private static String relationships() {
        return "<?xml version=\"1.0\"?><Relationships>" +
                "<Relationship Id=\"rImg\" Target=\"media/test.png\"/>" +
                "<Relationship Id=\"rHeader\" Target=\"header1.xml\"/>" +
                "<Relationship Id=\"rFooter\" Target=\"footer1.xml\"/>" +
                "</Relationships>";
    }

    private static String numbering() {
        return "<?xml version=\"1.0\"?><w:numbering xmlns:w=\"urn:w\">" +
                "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/>" +
                "<w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/></w:lvl></w:abstractNum>" +
                "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>";
    }

    private static String part(String root, String text) {
        return "<?xml version=\"1.0\"?><w:" + root + " xmlns:w=\"urn:w\"><w:p><w:r><w:t>" +
                text + "</w:t></w:r></w:p></w:" + root + ">";
    }

    private static void add(ZipOutputStream zip, String name, String value) throws Exception {
        add(zip, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void add(ZipOutputStream zip, String name, byte[] value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
    }
}
