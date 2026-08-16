package com.ashutosh.dotnetinterviewhub;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

public final class ContentFormatter {
    private static final int BLUE = Color.rgb(46, 116, 181);
    private static final int DARK_BLUE = Color.rgb(31, 77, 120);

    private ContentFormatter() {}

    public static SpannableStringBuilder format(String content) {
        SpannableStringBuilder output = new SpannableStringBuilder();
        String[] lines = content.replace("\r", "").split("\n", -1);
        for (String raw : lines) {
            String line = raw;
            int level = 0;
            if (line.startsWith("### ")) { level = 3; line = line.substring(4); }
            else if (line.startsWith("## ")) { level = 2; line = line.substring(3); }
            else if (line.startsWith("# ")) { level = 1; line = line.substring(2); }

            int start = output.length();
            output.append(line).append('\n');
            int end = output.length() - 1;
            if (level > 0 && end > start) {
                output.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                output.setSpan(new ForegroundColorSpan(level == 1 ? DARK_BLUE : BLUE), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                float size = level == 1 ? 1.32f : level == 2 ? 1.17f : 1.08f;
                output.setSpan(new RelativeSizeSpan(size), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return output;
    }
}
