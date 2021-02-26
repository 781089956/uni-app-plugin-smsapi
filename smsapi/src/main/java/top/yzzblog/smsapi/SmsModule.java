package top.yzzblog.smsapi;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;


/**
 * @author yztz
 *
 * <p>关于SDK相关说明</p>
 * <em>小于API 23(6.0) 不需要运行时权限，可以直接读取短信；反之，需要申请运行时权限 requestPermission()</em> <br />
 * <em>大于等于API 19(4.4) 需要设置默认app才可写入</em>
 *
 * <p>SMS-API参数说明：</p>
 * <p>所有用于传递SMS消息的JSON对象的字段命名统一为如下名称</p>
 * <ol>
 *     <li>{@code _id} 短信的编号id，自增字段</li>
 *     <li>{@code date} 短信发送时间，为Unix标准时间戳，单位（毫秒）</li>
 *     <li>{@code type} 短信来源，1-接收，2-发送</li>
 *     <li>{@code body} 短信内容</li>
 *     <li>{@code sub_id} 所属sim卡id，默认情况下为-1</li>
 *     <li>{@code thread_id} 短信会话id，一个address对应一个thread_id</li>
 *     <li>{@code address} 来信号码</li>
 * </ol>
 *
 * <p>主要API</p>
 *
 * @see #addSmsMsg(JSONObject)
 * @see #delSmsMsg(int)
 * @see #registerOnReceiveCallback(UniJSCallback)
 * @see #checkPermission()
 * @see #requestPermission(UniJSCallback)
 * @see #isDefaultApp()
 * @see #setDefaultApp()
 * @see #restoreDefaultApp()
 *
 */
public class SmsModule extends UniModule {
    /**
     * 日志标签
     */
    public static final String TAG = "SmsModule";
    /**
     * 接收短信用 ACTION
     */
    public static final String SMS_RECEIVE_ACTION = "top.yzzblog.intent.SMS_RECEIVE";

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

    private BroadcastReceiver receiver = null;


    /**
     * 检查是否已经获取SMS相关权限
     *
     * @return API小于23，将永远返回true
     */
    @UniJSMethod(uiThread = false)
    public boolean checkPermission() {

        Context context = mUniSDKInstance.getContext();

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
     * <p>注册收到新消息时的回调函数</p>
     *
     * <em>注意：在未注册之前的所有新短信将会被丢弃</em>
     *
     * <p>回调参数 {@code JSONObject}将包含如下字段</p>
     * <ol>
     *     <li>{@code address} 来信号码</li>
     *     <li>{@code type} <em>恒为1</em></li>
     *     <li>{@code date} 短信发送时间，为Unix标准时间戳，单位（毫秒）</li>
     *     <li>{@code sub_id} 所属sim卡id，默认情况下为-1</li>
     *     <li>{@code body} 短信内容</li>
     * </ol>
     *
     * @param callback 回调函数，届时会返回一个包含新短信信息的JSON对象
     */
    @UniJSMethod(uiThread = false)
    public void registerOnReceiveCallback(UniJSCallback callback) {
        Context context = mUniSDKInstance.getContext();
        //之前注册过
        if (null != receiver) {
            context.unregisterReceiver(receiver);
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle bundle = intent.getExtras();
                if (null != bundle) {
                    StringBuilder messageBody = new StringBuilder();
                    String phoneNumber = null;
                    Long time = null;

                    // SUB_ID
                    Integer subId = bundle.getInt("subscription");

                    Object[] pdus = (Object[]) bundle.get("pdus");
                    SmsMessage[] msg = new SmsMessage[pdus.length];

                    for (int i = 0; i < pdus.length; i++) {
                        msg[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                    if (msg.length > 0) {
                        // 内容
                        for (SmsMessage currMsg : msg) {
                            messageBody.append(currMsg.getDisplayMessageBody());
                        }
                        // 发信人
                        phoneNumber = msg[0].getDisplayOriginatingAddress();
                        time = msg[0].getTimestampMillis();
                    }

                    JSONObject data = new JSONObject();
                    data.put("address", phoneNumber);
                    data.put("type", 1);
                    data.put("date", time);
                    data.put("body", messageBody);
                    data.put("sub_id", subId);

                    callback.invokeAndKeepAlive(data);

                } else {
                    callback.invokeAndKeepAlive(null);
                }
            }
        };
        IntentFilter filter = new IntentFilter(SMS_RECEIVE_ACTION);
        context.registerReceiver(receiver, filter);


        log("注册成功");
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


    /**
     * 设置当前app为短信默认
     * 注意：该方法不能确保默认短信应用被正确设置，在后来操作中，最好调用isDefaultApp()来二次验证
     *
     * 注：原因由于“android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED”失效
     */
    @UniJSMethod
    public void setDefaultApp() {
        Context context = mUniSDKInstance.getContext();

        String currentPn = context.getPackageName();//获取当前程序包名
        if (!isDefaultApp()) {

            //第一次执行 记录系统默认的短信应用
            if (system_default_SMSApp_package == null) {
                SharedPreferences sp = context.getSharedPreferences("system_default_SMSApp_package", Context.MODE_PRIVATE);
                String sys_package = sp.getString("system_default_SMSApp_package", null);

                // 还没被记录
                if (null == sys_package) {
                    system_default_SMSApp_package = Telephony.Sms.getDefaultSmsPackage(context);    //获取手机当前设置的默认短信应用的包名
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("system_default_SMSApp_package", system_default_SMSApp_package);
                    editor.apply();
                } else {    // 已经被记录，则获取
                    system_default_SMSApp_package = sys_package;
                }
            }

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

        if (isDefaultApp()) {

            if (null == system_default_SMSApp_package) {
                SharedPreferences sp = context.getSharedPreferences("system_default_SMSApp_package", Context.MODE_PRIVATE);
                system_default_SMSApp_package = sp.getString("system_default_SMSApp_package", null);
            }

            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, system_default_SMSApp_package);
            context.startActivity(intent);
        }
    }

    @Override
    public void onActivityCreate() {
        super.onActivityCreate();
        log("*********************************");
    }

    /**
     * 判断当前的App是否为系统默认短信App
     *
     * @return 是否是默认短信app的布尔值
     */
    @UniJSMethod(uiThread = false)
    public boolean isDefaultApp() {
        Context context = mUniSDKInstance.getContext();
        String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context);//获取手机当前设置的默认短信应用的包名;
        String currentPn = context.getPackageName();//获取当前程序包名

        return defaultSmsApp.equals(currentPn);

    }

    /**
     * 删除指定id短信
     *
     * @param id 要删除的短信_id
     * @return -1：删除失败
     *         > 0: 删除成功，返回id
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
     * 添加短信记录<br/>
     * 说明: 对于android4.4（API 19）及以上版本，短信的写入/删除操作，都需要设置为默认短信应用<br/>
     * <em>注意：现所有字段均统一为类注释字段</em><br/>
     * <em>警告: 对于thread_id，如果在未知状态下可不传递或传递空值（系统会自动归类），切勿在不确定号码与thread_id映射关系下进行赋值，否则将会造成短信消息紊乱</em>
     *
     * @param obj 要新增的消息的 {@code JSONObject}
     * @return -1：添加失败（权限不足）
     *         > 0：添加成功，返回id
     */
    @UniJSMethod(uiThread = false)
    public int addSmsMsg(JSONObject obj) {
        Context context = mUniSDKInstance.getContext();
        if (!isDefaultApp()) return -1;

        String source = obj.getString("type");
        String number = obj.getString("address");
        String content = obj.getString("body");
        String time = obj.getString("date");
        String conversationID = obj.getString("thread_id");
        String SIMCardID = obj.getString("sub_id");

        ContentValues cv = new ContentValues();

        cv.put("type", source);
        cv.put("address", number);
        cv.put("body", content);
        cv.put("date", time);
        if (null != conversationID) cv.put("thread_id", conversationID);
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


    /**
     * 测试用 读取短信
     */
    @UniJSMethod(uiThread = false)
    public void readSmsMsg() {
        Context context = mUniSDKInstance.getContext();
        Uri sms_inbox = Uri.parse("content://sms/");

        Cursor cur = context.getContentResolver().query(sms_inbox, null, null, null, "date desc");

//        for (int i = 0; i < Objects.requireNonNull(cur).getColumnCount(); i++) {
//            log(cur.getColumnName(i));
//        }

        printMsg(cur, new String[]{"_id", "thread_id", "body"}, 20);

        if (!cur.isClosed()) {
            cur.close();
        }
    }


    /**
     * 测试用，打印短信内容
     * @param cur
     * @param columns
     * @param limit
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

    /**
     * 测试用 表明插件是否注册成功
     */
    @UniJSMethod
    public void test() {
        log("插件加载成功");
    }

    /**
     * 用于logcat的日志记录
     * @param msg 消息
     */
    @UniJSMethod(uiThread = false)
    public void log(String msg) {
        Log.i(TAG, msg);
    }


}
