import com.ashutosh.dotnetinterviewhub.DocxTextExtractor;

import java.io.FileInputStream;

public class ExtractorHarness {
    public static void main(String[] args) throws Exception {
        try (FileInputStream input = new FileInputStream(args[0])) {
            String text = DocxTextExtractor.extract(input);
            if (text.length() < 100) throw new IllegalStateException("Too little content extracted");
            System.out.println(text.length());
            System.out.println(text.substring(0, Math.min(160, text.length())).replace('\n', ' '));
        }
    }
}
