package top.yzzblog.smsapi;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

import static android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED;
import static android.provider.Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP;

public class SmsModule extends UniModule {
    public static final String TAG = "SmsModule";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private UniJSCallback reqPerCallback;
    private UniJSCallback changeDefAppCallback;

    private String system_default_SMSApp_package = null;

    private BroadcastReceiver default_SMS_changed_receiver = null;

    @Override
    public void onActivityStart() {
        super.onActivityStart();

    }

    /*
            检测并申请权限(短信相关)
             */
    @UniJSMethod(uiThread = false)
    public JSONObject checkPermission() {
        JSONObject data = new JSONObject();
        Context context = mUniSDKInstance.getContext();
        List<String> permissions = new ArrayList<>();
        for (String permission : PERMISSIONS_STORAGE) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission);
            }
        }
        if (permissions.isEmpty()) {
            data.put("code", "success");
            log("权限已获取");
        } else {
            data.put("code", "fail");
            log("权限未获取");
        }

        return data;
//        JSONObject data = new JSONObject();
//        data.put("code", "success");
//        return data;
    }

    @UniJSMethod
    public void requestPermission(UniJSCallback callback) {
        Context context = mUniSDKInstance.getContext();
        List<String> permissions = new ArrayList<>();
        for (String permission : PERMISSIONS_STORAGE) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission);
            }
        }
        if (permissions.isEmpty()) {
            log("全部权限已获取");

            JSONObject data = new JSONObject();
            data.put("code", "success");
            callback.invoke(data);
        } else {
            this.reqPerCallback = callback;
            log("开始请求权限");
            ((Activity) context).requestPermissions(permissions.toArray(new String[0]), REQUEST_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (reqPerCallback == null) return;

        JSONObject data = new JSONObject();

        if (grantResults.length > 0) {
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    data.put("code", "fail");
                    log("部分权限未请求成功");
                    reqPerCallback.invoke(data);
                    reqPerCallback = null;
                    return;
                }
            }
            log("权限请求成功");
            data.put("code", "success");
        } else {
            log("权限请求失败");
            data.put("code", "fail");
        }
        reqPerCallback.invoke(data);
        reqPerCallback = null;
    }

//    private void registerBroadcastReceiver(Context context) {
//        default_SMS_changed_receiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                log("进入接收器");
//                if (intent.getAction().equals(ACTION_DEFAULT_SMS_PACKAGE_CHANGED) && changeDefAppCallback != null) {
//                    boolean hasChanged = intent.getBooleanExtra(EXTRA_IS_DEFAULT_SMS_APP, false);
//                    //成功改变默认SMS应用
//                    JSONObject data = new JSONObject();
//                    if (hasChanged) {
//                        log("默认应用改变成功");
//                        data.put("code", "success");
//                    } else {
//                        log("默认应用改变失败");
//                        data.put("code", "fail");
//                    }
//                    changeDefAppCallback.invoke(data);
//                    changeDefAppCallback = null;
//                }
//            }
//        };
//
//        //注册广播接收器
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(ACTION_DEFAULT_SMS_PACKAGE_CHANGED);
//        context.registerReceiver(default_SMS_changed_receiver, filter);
//        log("注册成功");
//
//    }

    /*
        设置当前app为短信默认
         */
    @UniJSMethod
    public void setDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        String currentPn = context.getPackageName();//获取当前程序包名
        if (!isDefaultApp()) {
            //注册广播
//            if (default_SMS_changed_receiver == null) registerBroadcastReceiver(context);

            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, currentPn);
//            this.changeDefAppCallback = callback;
            context.startActivity(intent);
        }
    }

    @UniJSMethod
    public void restoreDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        if (isDefaultApp() && system_default_SMSApp_package != null) {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, system_default_SMSApp_package);
            context.startActivity(intent);
        }
    }



    @UniJSMethod(uiThread = false)
    public boolean isDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        String defaultSmsApp = null;
        String currentPn = context.getPackageName();//获取当前程序包名
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context);//获取手机当前设置的默认短信应用的包名
            //第一次执行 记录系统默认的短信应用
            if (system_default_SMSApp_package == null)
                system_default_SMSApp_package = defaultSmsApp;
            return defaultSmsApp.equals(currentPn);
        }
        return false;
    }

    /*
     添加短信记录
     */
    @UniJSMethod(uiThread = false)
    public boolean addSmsMsg(JSONObject obj) {
        Context context = mUniSDKInstance.getContext();
        if (!isDefaultApp()) return false;

        String source = obj.getString("source");
        String number = obj.getString("number");
        String content = obj.getString("content");
        String time = obj.getString("time");
        String conversationID = obj.getString("conversationID");
        String SIMCardID = obj.getString("SIMCardID");

        Uri sms_inbox = Uri.parse("content://sms/");
        ContentValues cv = new ContentValues();

        cv.put("type", source);
        cv.put("address", number);
        cv.put("body", content);
        cv.put("date", time);
        cv.put("thread_id", conversationID);
        cv.put("sub_id", SIMCardID);

        Uri uri = context.getContentResolver().insert(Uri.parse("content://sms/"), cv);
        return true;
    }


    /*
    测试用 读取短信
     */
    @UniJSMethod(uiThread = false)
    public void readSmsMsg() {
        Context context = mUniSDKInstance.getContext();
        Uri sms_inbox = Uri.parse("content://sms/");

        Cursor cur = context.getContentResolver().query(sms_inbox, null, null, null, "date desc");

        for (int i = 0; i < cur.getColumnCount(); i++) {
            log(cur.getColumnName(i));
        }

        printMsg(cur, new String[]{"address", "thread_id", "date"}, 20);

        if (!cur.isClosed()) {
            cur.close();
            cur = null;
        }

    }


    /*
    测试用，打印短信内容
     */
    private void printMsg(Cursor cur, String[] columns, int limit) {
        HashMap<String, Integer> map = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (String c : columns) {
            map.put(c, cur.getColumnIndex(c));
            sb.append(c).append("\t");
        }
        //打印列名
        log(sb.toString());

        if (cur.moveToFirst()) {
            do {
                sb = new StringBuilder();
                for (String c : columns) {
                    sb.append(cur.getString(map.get(c))).append("\t");
                }
                log(sb.toString());

                limit--;
            } while (limit > 0 && cur.moveToNext());

        }
    }

    /*
    前往默认app设置界面
     */
    @UniJSMethod
    public void goDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        context.startActivity(intent);
    }

    @UniJSMethod
    public void test() {
        log("插件加载成功");
    }

    public static void log(String msg) {
        Log.i(TAG, msg);
    }

}
