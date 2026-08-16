package com.ashutosh.dotnetinterviewhub;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DocumentAdapter extends BaseAdapter {
    private final Context context;
    private List<DocumentItem> items = new ArrayList<>();

    public DocumentAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<DocumentItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public DocumentItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 16), Ui.dp(context, 13), Ui.dp(context, 16), Ui.dp(context, 13));
        card.setBackground(Ui.cardBackground(context));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6));
        card.setLayoutParams(cardParams);

        DocumentItem item = getItem(position);
        TextView title = new TextView(context);
        title.setText((item.bookmarked ? "★  " : "") + item.title);
        title.setTextColor(Ui.INK);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        card.addView(title);

        TextView metadata = new TextView(context);
        String origin = item.seeded ? "Original interview guide" : "Imported document";
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(item.updatedAt));
        metadata.setText(item.category + "  •  " + origin + "  •  " + date);
        metadata.setTextColor(Ui.MUTED);
        metadata.setTextSize(12);
        metadata.setPadding(0, Ui.dp(context, 6), 0, 0);
        card.addView(metadata);
        return card;
    }
}
