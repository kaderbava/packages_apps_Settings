package com.android.settings;

import android.content.Context;
import android.os.BatteryManager;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class AboutPhoneData {
    private static final String TAG = "AboutPhoneData";

    private Context context;
    private String codename;
    private String soc;
    private String battery;
    private String display;
    private String camera;

    private static final String PROP_CODENAME = "ro.infinity.codename";
    private static final String PROP_SOC = "ro.infinity.soc";
    private static final String PROP_BATTERY = "ro.infinity.battery";
    private static final String PROP_DISPLAY = "ro.infinity.display";
    private static final String PROP_CAMERA = "ro.infinity.camera";

    public AboutPhoneData(Context context) {
        this.context = context;
        initializeData();
    }

    private void initializeData() {
        this.codename = getSystemPropertyStrict(PROP_CODENAME);
        this.soc = getSystemPropertyStrict(PROP_SOC);
        this.battery = getSystemPropertyStrict(PROP_BATTERY);
        this.display = getSystemPropertyStrict(PROP_DISPLAY);
        this.camera = getSystemPropertyStrict(PROP_CAMERA);
    }

    private String getSystemPropertyStrict(String key) {
        String value = SystemProperties.get(key);
        return (value != null && !value.isEmpty()) ? value : "Unknown";
    }

    public String getCodename() {
        return codename;
    }

    public String getSoc() {
        return soc;
    }

    public String getBattery() {
        return battery;
    }

    public String getDisplay() {
        return display;
    }

    public String getCamera() {
        return camera;
    }

    private String getSoCInfo() {
        return SystemProperties.get("ro.board.platform", "Unknown");
    }

    private String getBatteryInfo() {
        return SystemProperties.get(PROP_BATTERY, "Unknown");
    }

    private String getDisplayInfo() {
        return SystemProperties.get(PROP_DISPLAY, "Unknown");
    }

    private String getCameraInfo() {
        return SystemProperties.get(PROP_CAMERA, "Unknown");
    }
}
