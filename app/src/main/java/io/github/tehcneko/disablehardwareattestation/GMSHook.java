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

    private static final Map<String, SpoofInfo> spoofInfoMap = new HashMap<>();

    private static class SpoofInfo {
        String brand;
        String model;

        SpoofInfo(String brand, String model) {
            this.brand = brand;
            this.model = model;
        }
    }

    static {
        spoofInfoMap.put("ru.andr7e.deviceinfohw", new SpoofInfo("asus", "ASUS_AI2201"));
        spoofInfoMap.put("com.ytheekshana.deviceinfo", new SpoofInfo("Sony", "SO-52A"));
        spoofInfoMap.put("flar2.devcheck", new SpoofInfo("Xiaomi", "2210132C"));
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
        setBuildField("MODEL", spoofInfo.model);

        // Log the spoofed values for verification
        Log.i(TAG, "Found" + packageName) ;
        Log.i(TAG, "Spoofed BRAND: " + spoofInfo.brand " MODEL: " + spoofInfo.model);
    }

    private void setBuildField(String key, String value) {
        try {
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof app." + key, e);
        }
    }
}
