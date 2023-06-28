package io.github.tehcneko.disablehardwareattestation;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.Build.VERSION;
import android.util.Log;
import android.os.Binder;

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

    private static final String TAG = "GmsCompat/Attestation";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PROCESS_UNSTABLE = "com.google.android.gms.unstable";
    private static final String PROVIDER_NAME = "AndroidKeyStore";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");
    private static final boolean DEBUG = false;
    private static boolean sIsGms = false;
    private static boolean sIsFinsky = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (PACKAGE_GMS.equals(loadPackageParam.packageName) &&
                PROCESS_UNSTABLE.equals(loadPackageParam.processName)) {
            sIsGms = true;

            final boolean was = isGmsAddAccountActivityOnTop(loadPackageParam.appContext);
            final ActivityManager.RunningTaskInfo runningTaskInfo = getRunningTaskInfo(loadPackageParam.appContext);
            final ActivityManager.OnTaskStackChangedListener taskStackChangedListener = new ActivityManager.OnTaskStackChangedListener() {
                @Override
                public void onTaskStackChanged() {
                    final boolean is = isGmsAddAccountActivityOnTop(loadPackageParam.appContext);
                    if (is ^ was) {
                        dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                                ", killing myself!"); // process will restart automatically later
                        Process.killProcess(Process.myPid());
                    }
                }
            };
            try {
                ActivityManager activityManager = (ActivityManager) loadPackageParam.appContext.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    activityManager.addOnTaskStackChangedListener(taskStackChangedListener);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to register task stack listener!", e);
            }
            if (was) return;

            setBuildField("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
            setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
            return;
        }

        spoofBuildGms();

        if (PACKAGE_FINSKY.equals(loadPackageParam.packageName)) {
            sIsFinsky = true;
        }

        if (sIsGms || sIsFinsky) {
            try {
                KeyStore keyStore = KeyStore.getInstance(PROVIDER_NAME);
                Field keyStoreSpi = keyStore.getClass().getDeclaredField("keyStoreSpi");
                keyStoreSpi.setAccessible(true);
                XposedHelpers.findAndHookMethod(keyStoreSpi.get(keyStore).getClass(), "engineGetCertificateChain", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isCallerSafetyNet() || sIsFinsky) {
                            param.setThrowable(new UnsupportedOperationException());
                        }
                    }
                });
                Log.d(TAG, "keystore hooked");
            } catch (Throwable t) {
                XposedBridge.log("keystore hook failed: " + Log.getStackTraceString(t));
            }
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

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop(Context context) {
        try {
            final ActivityManager.RunningTaskInfo runningTaskInfo = getRunningTaskInfo(context);
            return runningTaskInfo != null && runningTaskInfo.topActivity != null
                    && runningTaskInfo.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    private static ActivityManager.RunningTaskInfo getRunningTaskInfo(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For API level 23 and above
                return activityManager.getRunningTasks(1).get(0);
            } else {
                // For API level 21 to 22
                return activityManager.getRunningTasks(1).get(0);
            }
        }
        return null;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            return false;
        }
        return callingUid == gmsUid;
    }

    public static boolean shouldBypassCallPermission(Context context) {
        // GMS doesn't have READ_PHONE_STATE permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassCallPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            return false;
        }
        return callingUid == gmsUid;
    }

    private static boolean isCallerSafetyNet() {
        int callingUid = Binder.getCallingUid();
        String[] packages = XposedBridge.getXposedPackages();
        for (String packageName : packages) {
            try {
                int uid = XposedBridge.BOOTCLASSLOADER.loadClass("android.app.AppGlobals")
                        .getMethod("getPackageManager")
                        .invoke(null)
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

    private static void dlog(String message) {
        if (DEBUG) {
            XposedBridge.log(TAG + ": " + message);
        }
    }
}
