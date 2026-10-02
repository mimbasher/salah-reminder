package com.salah.reminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pick a city from the bundled list: tap a country, then a city. No typing, no internet. */
public class CityPickerActivity extends Activity {
    static final String EX_NAME = "name", EX_LAT = "lat", EX_LNG = "lng";

    /** The whole list, parsed once and kept for as long as the process lives. */
    private static String[] cc, names, regions, lowerNames;
    private static double[] lats, lngs;

    private TextView title, status;
    private EditText search;
    private ListView list;
    private Adapter adapter;

    private String country;                    // null while the country list is showing
    private final List<Row> rows = new ArrayList<>();

    private static class Row {
        String label, sub, code;
        int index = -1;                        // into the parallel city arrays
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(dp(18), dp(16), dp(18), 0);

        LinearLayout head = Ui.rowOf(this);
        head.addView(Ui.iconButton(this, "←", v -> up()));
        title = Ui.text(this, "Choose your city", 21, Ui.TEXT, Ui.MEDIUM);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title);
        root.addView(head, Ui.lp(-1, -2));

        search = Ui.input(this, "Or type to search any city");
        root.addView(search, Ui.stacked(this, 14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable e) { rebuild(); }
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
        });

        status = Ui.text(this, "Loading cities…", 14, Ui.MUTED, null);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(28), 0, 0);
        root.addView(status, Ui.lp(-1, -2));

        list = new ListView(this);
        list.setDivider(new ColorDrawable(Ui.LINE));
        list.setDividerHeight(1);
        list.setFastScrollEnabled(true);
        list.setVisibility(View.GONE);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> tapped(rows.get(pos)));
        root.addView(list, Ui.lp(-1, 0, 1));

        setContentView(root);

        if (names == null) new Thread(this::load).start();
        else rebuild();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------------- data ----------------

    private void load() {
        List<String[]> parsed = new ArrayList<>(35000);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(getAssets().open("cities.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length >= 5) parsed.add(f);
            }
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("Couldn't read the city list — use GPS or coordinates."));
            return;
        }
        int n = parsed.size();
        String[] c = new String[n], nm = new String[n], rg = new String[n], lw = new String[n];
        double[] la = new double[n], ln = new double[n];
        for (int i = 0; i < n; i++) {
            String[] f = parsed.get(i);
            c[i] = f[0];
            nm[i] = f[1];
            rg[i] = f[2];
            lw[i] = f[1].toLowerCase(Locale.US);
            try {
                la[i] = Double.parseDouble(f[3]);
                ln[i] = Double.parseDouble(f[4]);
            } catch (NumberFormatException ignored) { }
        }
        cc = c; names = nm; regions = rg; lowerNames = lw; lats = la; lngs = ln;
        runOnUiThread(this::rebuild);
    }

    private static String countryName(String code) {
        String s = new Locale("", code).getDisplayCountry();
        return s == null || s.isEmpty() ? code : s;
    }

    // ---------------- list contents ----------------

    private void rebuild() {
        if (names == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        rows.clear();
        if (!q.isEmpty()) searchRows(q);
        else if (country == null) countryRows();
        else cityRows(country);

        title.setText(!q.isEmpty() ? "Search results"
                : country == null ? "Choose your city" : countryName(country));
        status.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (rows.isEmpty()) {
            status.setText(q.isEmpty() ? "Nothing here."
                    : "No city called “" + search.getText() + "”.\n"
                    + "Try fewer letters, or use GPS in Settings.");
        }
        adapter.notifyDataSetChanged();
        list.setSelectionAfterHeaderView();
    }

    private void countryRows() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : cc) {
            Integer k = counts.get(code);
            counts.put(code, k == null ? 1 : k + 1);
        }
        List<Row> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Row r = new Row();
            r.code = e.getKey();
            r.label = countryName(e.getKey());
            r.sub = e.getValue() + (e.getValue() == 1 ? " city" : " cities");
            out.add(r);
        }
        Collections.sort(out, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        rows.addAll(out);
    }

    private void cityRows(String code) {
        for (int i = 0; i < names.length; i++) {
            if (!code.equals(cc[i])) continue;
            rows.add(cityRow(i, false));
        }
    }

    /** Names that start with the query first, then names that merely contain it. */
    private void searchRows(String q) {
        List<Row> starts = new ArrayList<>(), contains = new ArrayList<>();
        for (int i = 0; i < lowerNames.length && starts.size() + contains.size() < 400; i++) {
            if (lowerNames[i].startsWith(q)) starts.add(cityRow(i, true));
            else if (q.length() >= 3 && lowerNames[i].contains(q)) contains.add(cityRow(i, true));
        }
        rows.addAll(starts);
        rows.addAll(contains);
    }

    private Row cityRow(int i, boolean withCountry) {
        Row r = new Row();
        r.index = i;
        r.label = names[i];
        StringBuilder sb = new StringBuilder();
        if (!regions[i].isEmpty()) sb.append(regions[i]);
        if (withCountry) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(countryName(cc[i]));
        }
        r.sub = sb.toString();
        return r;
    }

    // ---------------- interaction ----------------

    private void tapped(Row r) {
        if (r.index < 0) {                     // a country: show its cities
            country = r.code;
            search.setText("");
            rebuild();
            return;
        }
        int i = r.index;
        String place = names[i] + ", " + countryName(cc[i]);
        setResult(RESULT_OK, new Intent()
                .putExtra(EX_NAME, place)
                .putExtra(EX_LAT, lats[i])
                .putExtra(EX_LNG, lngs[i]));
        finish();
    }

    /** Back goes up one level before it leaves the screen. */
    private void up() {
        if (!search.getText().toString().isEmpty()) {
            search.setText("");
        } else if (country != null) {
            country = null;
            rebuild();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        up();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View reuse, ViewGroup parent) {
            LinearLayout box;
            if (reuse instanceof LinearLayout) {
                box = (LinearLayout) reuse;
            } else {
                box = Ui.column(CityPickerActivity.this);
                box.setPadding(dp(6), dp(14), dp(6), dp(14));
                box.addView(Ui.text(CityPickerActivity.this, "", 16, Ui.TEXT, Ui.MEDIUM));
                box.addView(Ui.text(CityPickerActivity.this, "", 12, Ui.MUTED, null));
            }
            Row r = rows.get(pos);
            ((TextView) box.getChildAt(0)).setText(r.label);
            TextView sub = (TextView) box.getChildAt(1);
            sub.setText(r.sub);
            sub.setVisibility(r.sub.isEmpty() ? View.GONE : View.VISIBLE);
            return box;
        }
    }
}
