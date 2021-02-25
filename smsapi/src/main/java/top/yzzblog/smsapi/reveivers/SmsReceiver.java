package top.yzzblog.smsapi.reveivers;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;

public class SmsReceiver extends BroadcastReceiver {
    private String phoneNumber;

    @Override
    public void onReceive(Context context, Intent intent) {
        StringBuilder messageBody = new StringBuilder();
        Bundle bundle = intent.getExtras();
        if (null != bundle) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            SmsMessage[] msg = new SmsMessage[pdus.length];
            for (int i = 0; i < pdus.length; i++) {
                msg[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }
            for (SmsMessage currMsg : msg) {
                messageBody.append(currMsg.getDisplayMessageBody());
                phoneNumber = currMsg.getDisplayOriginatingAddress();
            }
            //提醒新消息
            Toast.makeText(context, "新消息： " + messageBody.toString(), Toast.LENGTH_LONG).show();
            //将新消息存至数据库
            addSmsToDB(context, phoneNumber, messageBody.toString());
        }

    }

    private void addSmsToDB(Context context, String address, String content) {
        ContentValues values = new ContentValues();
        values.put("date", System.currentTimeMillis());
        values.put("read", 0);//0为未读信息
        values.put("type", 1);//1为收件箱信息
        values.put("address", address);
        values.put("body", content);
        context.getContentResolver().insert(Uri.parse("content://sms/"), values);
    }

}

