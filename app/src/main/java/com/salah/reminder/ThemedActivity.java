package com.salah.reminder;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

/**
 * Applies the light/dark choice before any view is built. The setting can override the system,
 * so each screen forces its own configuration rather than trusting the phone's.
 */
abstract class ThemedActivity extends Activity {
    private boolean builtDark;

    @Override
    protected void attachBaseContext(Context base) {
        try {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (Ui.nightWanted(base) ? Configuration.UI_MODE_NIGHT_YES
                                            : Configuration.UI_MODE_NIGHT_NO);
            applyOverrideConfiguration(config);
        } catch (Exception ignored) {
            // Already resolved its resources; the palette below still matches the setting.
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.apply(this);
        builtDark = Ui.dark;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (builtDark != Ui.nightWanted(this)) recreate();   // setting changed on another screen
        else Ui.apply(this);                                 // keep the shared palette current
    }
}
