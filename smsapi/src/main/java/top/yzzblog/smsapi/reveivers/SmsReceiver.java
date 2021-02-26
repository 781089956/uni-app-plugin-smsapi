package top.yzzblog.smsapi.reveivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static top.yzzblog.smsapi.SmsModule.SMS_RECEIVE_ACTION;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Intent intent1 = new Intent(SMS_RECEIVE_ACTION);
        intent1.putExtras(intent.getExtras());
        context.sendBroadcast(intent1);
    }

//    private void addSmsToDB(Context context, String address, String content) {
//        ContentValues values = new ContentValues();
//        values.put("date", System.currentTimeMillis());
//        values.put("read", 0);//0为未读信息
//        values.put("type", 1);//1为收件箱信息
//        values.put("address", address);
//        values.put("body", content);
//        context.getContentResolver().insert(Uri.parse("content://sms/"), values);
//    }

}

