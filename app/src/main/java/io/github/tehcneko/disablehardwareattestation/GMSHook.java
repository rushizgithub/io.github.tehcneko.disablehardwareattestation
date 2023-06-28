package io.github.tehcneko.disablehardwareattestation;

import android.annotation.SuppressLint;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.Build.VERSION;
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

             final boolean was = isGmsAddAccountActivityOnTop();
                final TaskStackListener taskStackListener = new TaskStackListener() {
                    @Override
                    public void onTaskStackChanged() {
                        final boolean is = isGmsAddAccountActivityOnTop();
                        if (is ^ was) {
                            dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                                    ", killing myself!"); // process will restart automatically later
                            Process.killProcess(Process.myPid());
                        }
                    }
                };
                try {
                    ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to register task stack listener!", e);
                }
                if (was) return;

                setPropValue("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
                setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
            } else if (processName.toLowerCase().contains("persistent")
                        || processName.toLowerCase().contains("ui")
                        || processName.toLowerCase().contains("learning")) {
                propsToChange.putAll(propsToChangePixel6Pro);
            }
            return;
        }
    
            spoofBuildGms();
        }
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

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }
    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

}
