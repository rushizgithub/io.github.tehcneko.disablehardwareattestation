package io.github.tehcneko.disablehardwareattestation;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GMSHook implements IXposedHookLoadPackage {

    private static final String TAG = "GMSHook";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String GMS_ADD_ACCOUNT_ACTIVITY = "com.google.android.gms.common.account.AccountPickerActivity";

    private static final boolean DEBUG = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (PACKAGE_GMS.equals(loadPackageParam.packageName)) {
            hookKeyStore(loadPackageParam);
            spoofBuildGms();
        }
    }

    private static void hookKeyStore(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        try {
            Class<?> keyStore = Class.forName("java.security.KeyStore");
            Field providerName = keyStore.getDeclaredField("PROVIDER_NAME");
            providerName.setAccessible(true);
            String PROVIDER_NAME = (String) providerName.get(null);

            Field instance = keyStore.getDeclaredField("instance");
            instance.setAccessible(true);
            Object keyStoreInstance = instance.get(null);
            Field keyStoreSpi = keyStoreInstance.getClass().getDeclaredField("keyStoreSpi");
            keyStoreSpi.setAccessible(true);

            XposedHelpers.findAndHookMethod(keyStoreSpi.get(keyStoreInstance).getClass(), "engineGetCertificateChain", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isCallerSafetyNet() || sIsFinsky) {
                        param.setThrowable(new UnsupportedOperationException());
                    }
                }
            });

            Log.d(TAG, "Keystore hooked");
        } catch (Throwable t) {
            XposedBridge.log("Keystore hook failed: " + Log.getStackTraceString(t));
        }
    }

    private static void spoofBuildGms() {
        // Alter model name and fingerprint to avoid hardware attestation enforcement
        setBuildField("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
        setBuildField("PRODUCT", "walleye");
        setBuildField("DEVICE", "walleye");
        setBuildField("MODEL", "Pixel 2");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.O);
    }

    private static void setBuildField(String key, Object value) {
        try {
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, int value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.setInt(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop(Context context) {
        try {
            final ActivityManager.RunningTaskInfo runningTaskInfo = getRunningTaskInfo(context);
            if (runningTaskInfo != null && runningTaskInfo.topActivity != null) {
                String topActivityName = runningTaskInfo.topActivity.getClassName();
                return GMS_ADD_ACCOUNT_ACTIVITY.equals(topActivityName);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static ActivityManager.RunningTaskInfo getRunningTaskInfo(Context context) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            return activityManager.getRunningTasks(1).get(0);
        }
        return null;
    }

    private static boolean isCallerSafetyNet() {
        int callingUid = Binder.getCallingUid();
        String[] packages = XposedBridge.getXposedPackages();
        for (String packageName : packages) {
            try {
                int uid = (int) XposedHelpers.callStaticMethod(Class.forName("android.app.ActivityThread"), "getPackageManager")
                        .getClass()
                        .getMethod("getPackageUid", String.class, int.class)
                        .invoke(null, packageName, 0);
                if (uid == callingUid) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

}
