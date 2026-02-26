package com.raman.carriermod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class CarrierMod implements IXposedHookLoadPackage {

    private static float currentA = 0;
    private static float voltageV = 0;
    private static boolean isReceiverRegistered = false;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals("android") &&
                !lpparam.packageName.equals("com.android.systemui") &&
                !lpparam.packageName.equals("com.android.settings")) {
            return;
        }

        String customCarrier = getSystemProperty("persist.sys.custom_carrier", "");
        if (customCarrier != null && !customCarrier.isEmpty()) {
            try {
                XposedHelpers.findAndHookMethod("android.telephony.SubscriptionInfo", lpparam.classLoader,
                        "getCarrierName", XC_MethodReplacement.returnConstant(customCarrier));
                XposedHelpers.findAndHookMethod("android.telephony.SubscriptionInfo", lpparam.classLoader,
                        "getDisplayName", XC_MethodReplacement.returnConstant(customCarrier));
                XposedHelpers.findAndHookMethod("android.telephony.ServiceState", lpparam.classLoader,
                        "getOperatorAlphaLong", XC_MethodReplacement.returnConstant(customCarrier));
                XposedHelpers.findAndHookMethod("android.telephony.ServiceState", lpparam.classLoader,
                        "getOperatorAlphaShort", XC_MethodReplacement.returnConstant(customCarrier));
            } catch (Throwable t) {
                de.robv.android.xposed.XposedBridge.log("CarrierMod Error: " + t.getMessage());
            }
        }

        boolean chargingModEnabled = getSystemProperty("persist.sys.charging_mod", "false").equals("true");
        if (chargingModEnabled && lpparam.packageName.equals("com.android.systemui")) {
            hookChargingStats(lpparam);
        }
    }

    private void hookChargingStats(LoadPackageParam lpparam) {
        XC_MethodHook powerTextHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                if (context == null) return;

                if (!isReceiverRegistered) {
                    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    context.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context c, Intent i) {
                            currentA = i.getIntExtra("max_charging_current", 0) / 1000000f;
                            voltageV = i.getIntExtra("max_charging_voltage", 0) / 1000000f;
                        }
                    }, filter);
                    isReceiverRegistered = true;
                }

                CharSequence result = (CharSequence) param.getResult();
                if (result == null || result.length() == 0) return;

                Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryStatus == null) return;

                int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                if (!isCharging) return;

                int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);

                float watts = currentA * voltageV;

                String original = result.toString();
                String eta = original;
                if (original.contains("•")) {
                    String[] parts = original.split("•");
                    eta = parts[parts.length - 1].trim();
                } else if (original.contains("-")) {
                    String[] parts = original.split("-");
                    eta = parts[parts.length - 1].trim();
                }

                String customStats = String.format("%d%% • %.2fA • %.1fV • %.1fW", batteryPct, currentA, voltageV, watts);

                if (eta.equalsIgnoreCase("Charging") || eta.toLowerCase().contains("charging rapidly") || eta.toLowerCase().contains("charging slowly")) {
                    param.setResult(customStats);
                } else {
                    param.setResult(customStats + "\n" + eta);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "computePowerIndication", powerTextHook);
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle", lpparam.classLoader, "computePowerIndication", powerTextHook);
        } catch (Throwable t) {}
    }

    private String getSystemProperty(String key, String def) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            return (String) systemProperties.getMethod("get", String.class, String.class).invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }
}