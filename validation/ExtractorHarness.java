import com.ashutosh.dotnetinterviewhub.DocxTextExtractor;
import com.ashutosh.dotnetinterviewhub.DocxHtmlExtractor;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExtractorHarness {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        String text = DocxTextExtractor.extract(new ByteArrayInputStream(bytes));
        String html = DocxHtmlExtractor.extract(new ByteArrayInputStream(bytes));
        if (text.length() < 100) throw new IllegalStateException("Too little content extracted");
        if (!html.contains("<table>") || !html.contains("Final 10-Minute Revision") ||
                !html.contains("Official References")) {
            throw new IllegalStateException("Formatted HTML is missing required DOCX structures");
        }
        if (!html.contains("1.&emsp;") || html.contains("44.&emsp;")) {
            throw new IllegalStateException("Formatted HTML did not preserve the corrected numbering");
        }
        System.out.println("TEXT_LENGTH=" + text.length());
        System.out.println("HTML_LENGTH=" + html.length());
        System.out.println(text.substring(0, Math.min(160, text.length())).replace('\n', ' '));
        if (args.length > 1) Files.write(Paths.get(args[1]), html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
