package com.grg.playground.accescontrol.Activities;

/**
 * Created by playground on 31/01/2017.
 */

public class Preferences extends BasicActivity  {

    public enum Preference {
        AutoReconnect("auto_reconnect"),
        SaveLastUsedKeyFiles("save_last_used_key_files"),
        UseCustomSectorCount("use_custom_sector_count"),
        CustomSectorCount("custom_sector_count");
        // Add more preferences here (comma separated).

        private final String text;

        private Preference(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }


}
