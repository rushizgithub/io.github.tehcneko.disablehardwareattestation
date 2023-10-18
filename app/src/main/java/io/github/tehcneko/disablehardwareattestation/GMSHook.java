package io.github.tehcneko.disablehardwareattestation;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.security.KeyStore;
import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedPrivateApi")
@SuppressWarnings("ConstantConditions")
public class GMSHook implements IXposedHookLoadPackage {

    private static final String TAG = GMSHook.class.getSimpleName();
    private static final String[] PACKAGE_1 = {
    "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn",
    "flar2.devcheck"
};

    private static final String[] PACKAGE_2 = {
    "ru.andr7e.deviceinfohw",
    "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
};

        private static final String[] PACKAGE_3 = {
"com.ea.gp.apexlegendsmobilefps",
            "com.levelinfinite.hotta.gp",
            "com.mobile.legends",
            "com.supercell.clashofclans",
            "com.tencent.tmgp.sgame",
            "com.vng.mlbbvn",
            "com.ytheekshana.deviceinfo"
            
};

    private static final String PACKAGE_3 = "flar2.devcheck";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (PACKAGE_1.equals(loadPackageParam.packageName)) {
            spoof1();
        }

        if (PACKAGE_2.equals(loadPackageParam.packageName)) {
            spoof2();
        }

        if (PACKAGE_3.equals(loadPackageParam.packageName)) {
            spoof3();
        }
    }

    private static void spoof1() {
        setBuildField("BRAND", "asus");
        setBuildField("MANUFACTURER", "asus");
        setBuildField("DEVICE", "AI2201");
        setBuildField("MODEL", "ASUS_AI2201");
    }

        private static void spoof2() {
        setBuildField("BRAND", "Sony");
        setBuildField("MANUFACTURER", "Sony");
        setBuildField("DEVICE", "Sony");
        setBuildField("MODEL", "SO-52A");
    }

        private static void spoof3() {
        setBuildField("BRAND", "Xiaomi");
        setBuildField("MANUFACTURER", "Xiaomi");
        setBuildField("DEVICE", "2210132C");
        setBuildField("MODEL", "2210132C");
    }

    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

}
