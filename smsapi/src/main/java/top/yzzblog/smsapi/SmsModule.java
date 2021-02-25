package top.yzzblog.smsapi;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;


public class SmsModule extends UniModule {
    /*
    关于SDK相关说明：
        < API 23(6.0) 不需要运行时权限，可以直接读取短信；反之，需要申请运行时权限 requestPermission()
        >= API 19(4.4) 需要设置默认app才可写入
     */


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

    private String system_default_SMSApp_package = null;


    @Override
    public void onActivityStart() {
        super.onActivityStart();

    }

    /**
     * 检查是否已经获取SMS相关权限
     *
     * @return API < 23 永远返回true
     */
    @UniJSMethod(uiThread = false)
    public boolean checkPermission() {

        Context context = mUniSDKInstance.getContext();
        JSONObject data = new JSONObject();

        // SDK >= 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            for (String permission : PERMISSIONS_STORAGE) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(permission);
                }
            }
            return permissions.isEmpty();
        }
        return true;
    }

    /**
     * 申请SMS相关权限
     *
     * @param callback 回调函数，参数为JSON对象{"code":"success/fail"}，代表权限是否申请成功
     */
    @UniJSMethod
    public void requestPermission(UniJSCallback callback) {
        Context context = mUniSDKInstance.getContext();
        List<String> permissions = new ArrayList<>();
        // SDK >= 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } else {
            //否则直接回调
            JSONObject data = new JSONObject();
            data.put("code", "success");
            callback.invoke(data);
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

    /**
     * 设置当前app为短信默认
     * 注意：该方法不能确保默认短信应用被正确设置，在后来操作中，最好调用isDefaultApp()来二次验证
     * <p>
     * “android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED”
     */
    @UniJSMethod
    public void setDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        String currentPn = context.getPackageName();//获取当前程序包名
        if (!isDefaultApp()) {
            //注册广播
            //接收设置的结果的广播接收器工作未能达到预期 @see ACTION_DEFAULT_SMS_PACKAGE_CHANGED
//            if (default_SMS_changed_receiver == null) registerBroadcastReceiver(context);

            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, currentPn);
            context.startActivity(intent);
        }
    }

    /**
     * 恢复为系统原来的短信App（同上）
     */
    @UniJSMethod
    public void restoreDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        if (isDefaultApp() && system_default_SMSApp_package != null) {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, system_default_SMSApp_package);
            context.startActivity(intent);
        }
    }

    /**
     * 判断当前的App是否为系统默认短信App
     *
     * @return
     */
    @UniJSMethod(uiThread = false)
    public boolean isDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        String defaultSmsApp = null;
        String currentPn = context.getPackageName();//获取当前程序包名
        defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context);//获取手机当前设置的默认短信应用的包名
        //第一次执行 记录系统默认的短信应用
        if (system_default_SMSApp_package == null)
            system_default_SMSApp_package = defaultSmsApp;
        return defaultSmsApp.equals(currentPn);

    }

    /**
     * 删除指定id短信
     *
     * @param id
     * @return -1：删除失败 > 0: 删除成功，返回id
     */
    @UniJSMethod(uiThread = false)
    public int delSmsMsg(int id) {
        Context context = mUniSDKInstance.getContext();
        if (!isDefaultApp()) return -1;


        Uri sms_inbox = Uri.parse("content://sms/");
        context.getContentResolver().delete(sms_inbox, "_id=?", new String[]{String.valueOf(id)});

        return id;
    }

    /**
     * 添加短信记录
     * 说明: 对于android4.4（API 19）及以上版本，短信的写入/删除操作，都需要设置为默认短信应用
     *
     * @param obj
     * @return -1：添加失败（权限不足） > 0：添加成功，返回id
     */
    @UniJSMethod(uiThread = false)
    public int addSmsMsg(JSONObject obj) {
        Context context = mUniSDKInstance.getContext();
        if (!isDefaultApp()) return -1;

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

        if (null == uri || null == uri.getLastPathSegment()) return -1;

        return Integer.parseInt(uri.getLastPathSegment());
    }

    /**
     * 获取全部短信对象
     *
     * @return 短信对象数组
     */
    @UniJSMethod(uiThread = false)
    public JSONArray getAllMsg() {
        JSONArray ret = new JSONArray();

        Context context = mUniSDKInstance.getContext();
        String[] columns = {"_id", "type", "address", "body", "date", "thread_id", "sub_id"};
        HashMap<String, Integer> map = new HashMap<>();

        Uri sms_inbox = Uri.parse("content://sms/");
        Cursor cur = context.getContentResolver().query(sms_inbox, null, null, null, "date desc");

        for (String c : columns) {
            map.put(c, cur.getColumnIndex(c));
        }

        if (cur.moveToFirst()) {
            do {
                JSONObject obj = new JSONObject();
                for (String c : columns) {
                    obj.put(c, cur.getString(map.get(c)));
                }
                ret.add(obj);
            } while (cur.moveToNext());
        }

        if (!cur.isClosed()) {
            cur.close();
        }

        return ret;
    }


    /*
    测试用 读取短信
     */
    @UniJSMethod(uiThread = false)
    public void readSmsMsg() {
        Context context = mUniSDKInstance.getContext();
        Uri sms_inbox = Uri.parse("content://sms/");

        Cursor cur = context.getContentResolver().query(sms_inbox, null, null, null, "date desc");

        for (int i = 0; i < Objects.requireNonNull(cur).getColumnCount(); i++) {
            log(cur.getColumnName(i));
        }

        printMsg(cur, new String[]{"_id", "thread_id", "sub_id"}, 20);

        if (!cur.isClosed()) {
            cur.close();
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


    @UniJSMethod
    public void test() {
        log("插件加载成功");
    }

    @UniJSMethod(uiThread = false)
    public void log(String msg) {
        Log.i(TAG, msg);
    }


}
