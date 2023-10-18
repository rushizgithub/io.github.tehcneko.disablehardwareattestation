package io.github.tehcneko.disablehardwareattestation;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GMSHook implements IXposedHookLoadPackage {

    private static final String TAG = GMSHook.class.getSimpleName();

    // Create a HashMap to map package names to spoof information
    private static final Map<String, SpoofInfo> spoofInfoMap = new HashMap<>();

    // Define SpoofInfo class to hold spoof values for a package
    private static class SpoofInfo {
        String brand;
        String manufacturer;
        String device;
        String model;

        SpoofInfo(String brand, String manufacturer, String device, String model) {
            this.brand = brand;
            this.manufacturer = manufacturer;
            this.device = device;
            this.model = model;
        }
    }

    static {
        // Populate the spoofInfoMap with package names and their respective spoof information
        spoofInfoMap.put("ru.andr7e.deviceinfohw", new SpoofInfo("asus", "asus", "AI2201", "ASUS_AI2201"));
        spoofInfoMap.put("com.ytheekshana.deviceinfo", new SpoofInfo("Sony", "Sony", "Sony", "SO-52A"));
        spoofInfoMap.put("flar2.devcheck", new SpoofInfo("Xiaomi", "Xiaomi", "2210132C", "2210132C"));
        // Add more packages and spoof information as needed
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        SpoofInfo spoofInfo = spoofInfoMap.get(loadPackageParam.packageName);
        if (spoofInfo != null) {
            spoofDeviceProperties(spoofInfo);
        }
    }

    private void spoofDeviceProperties(SpoofInfo spoofInfo) {
        setBuildField("BRAND", spoofInfo.brand);
        setBuildField("MANUFACTURER", spoofInfo.manufacturer);
        setBuildField("DEVICE", spoofInfo.device);
        setBuildField("MODEL", spoofInfo.model);
    }

    private void setBuildField(String key, String value) {
        try {
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }
}
