import com.ashutosh.dotnetinterviewhub.DocxTextExtractor;
import com.ashutosh.dotnetinterviewhub.DocxHtmlExtractor;
import com.ashutosh.dotnetinterviewhub.DocxSecurity;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExtractorHarness {
    public static void main(String[] args) throws Exception {
        boolean generatedFixture = args.length == 0;
        byte[] bytes = generatedFixture ? DocxFixtureFactory.create() : Files.readAllBytes(Paths.get(args[0]));
        String text = DocxTextExtractor.extract(new ByteArrayInputStream(bytes));
        String html = DocxHtmlExtractor.extract(new ByteArrayInputStream(bytes));
        boolean rejectedOversize = false;
        try { DocxSecurity.readLimited(new ByteArrayInputStream(new byte[6]), 5, "limit"); }
        catch (IllegalArgumentException expected) { rejectedOversize = true; }
        if (!rejectedOversize) throw new IllegalStateException("Input size limit was not enforced");
        if (text.length() < 40) throw new IllegalStateException("Too little content extracted");
        if (!html.contains("<table>")) throw new IllegalStateException("Formatted HTML is missing a table");
        if (generatedFixture) {
            require(html, "<span class=\"list-marker\">1.", "automatic numbering");
            require(html, "<span class=\"list-marker\">2.", "number incrementing");
            require(html, "colspan=\"2\"", "horizontal merged cells");
            require(html, "rowspan=\"2\"", "vertical merged cells");
            require(html, "data:image/png;base64,", "embedded images");
            require(html, "class=\"page-break\"", "page breaks");
            require(html, "class=\"doc-header\"", "headers");
            require(html, "class=\"doc-footer\"", "footers");
            boolean rejectedDoctype = false;
            try {
                DocxHtmlExtractor.extract(new ByteArrayInputStream(DocxFixtureFactory.createWithDoctype()));
            } catch (Exception expected) { rejectedDoctype = true; }
            if (!rejectedDoctype) throw new IllegalStateException("Unsafe XML DOCTYPE was not rejected");
        } else {
            if (!html.contains("Final 10-Minute Revision") || !html.contains("Official References"))
                throw new IllegalStateException("Formatted HTML is missing required React guide structures");
            if (!html.contains("1.&emsp;") || html.contains("44.&emsp;"))
                throw new IllegalStateException("Formatted HTML did not preserve the corrected numbering");
        }
        System.out.println("TEXT_LENGTH=" + text.length());
        System.out.println("HTML_LENGTH=" + html.length());
        System.out.println("WORD_COMPATIBILITY_CHECKS=PASSED");
        System.out.println("XML_SECURITY_CHECK=PASSED");
        System.out.println("SIZE_LIMIT_CHECK=PASSED");
        System.out.println(text.substring(0, Math.min(160, text.length())).replace('\n', ' '));
        if (args.length > 1) Files.write(Paths.get(args[1]), html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void require(String html, String expected, String feature) {
        if (!html.contains(expected)) throw new IllegalStateException("Missing compatibility feature: " + feature);
    }
}
