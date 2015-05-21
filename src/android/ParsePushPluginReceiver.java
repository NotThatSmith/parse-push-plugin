package com.phonegap.parsepushplugin;

import com.parse.ParsePushBroadcastReceiver;

import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.net.Uri;
import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;

import org.json.JSONObject;
import org.json.JSONException;


public class ParsePushPluginReceiver extends ParsePushBroadcastReceiver
{	
	public static final String LOGTAG = "ParsePushPluginReceiver";
	public static final String PARSE_DATA_KEY = "com.parse.Data";
	
	private static JSONObject MSG_COUNTS = new JSONObject();
	
	
	@Override
	protected void onPushReceive(Context context, Intent intent) {
		Log.d(LOGTAG, "onPushReceive - context: " + context);
		
		if(!ParsePushPlugin.isInForeground()){
			//
			// only create entry for notification tray if plugin/application is
			// not running in foreground.
			//
			// use tag + notification id=0 to limit the number of notifications on the tray
			// (older messages with the same tag and notification id will be replaced)
			NotificationManager notifManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notifManager.notify(getNotificationTag(context, intent), 0, getNotification(context, intent));
		}
		
	    //
	    // relay the push notification data to the javascript
		ParsePushPlugin.javascriptECB( getPushData(intent) );
	}
	
	@Override
    protected void onPushOpen(Context context, Intent intent) {
		Log.d(LOGTAG, "onPushOpen - context: " + context);
		
        JSONObject pnData = getPushData(intent);
        
        resetCount(getNotificationTag(context, pnData));
        
        String uriString = pnData.optString("uri");
        Class<? extends Activity> cls = getActivity(context, intent);
        
        Intent activityIntent;
        if (!uriString.isEmpty()) {
            activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
        } else {
            activityIntent = new Intent(context, cls);
        }
        activityIntent.putExtras(intent.getExtras());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(cls);
            stackBuilder.addNextIntent(activityIntent);
            stackBuilder.startActivities();
        } else {
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
    }
	
	@Override
	protected Notification getNotification(Context context, Intent intent){
		JSONObject pnData = getPushData(intent);
		String pnTag = getNotificationTag(context, pnData);
		
		Intent cIntent = new Intent(ACTION_PUSH_OPEN);
		Intent dIntent = new Intent(ACTION_PUSH_DELETE);
		
		cIntent.putExtras(intent).setPackage(context.getPackageName());
		dIntent.putExtras(intent).setPackage(context.getPackageName());
		
		PendingIntent contentIntent = PendingIntent.getBroadcast(context, 0, cIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		PendingIntent deleteIntent  = PendingIntent.getBroadcast(context, 0, dIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
		
		if(pnData.has("title")){
			builder.setTicker(pnData.optString("title")).setContentTitle(pnData.optString("title"));
		} else if(pnData.has("alert")){
			builder.setTicker(pnTag).setContentTitle(pnTag);
		}
		
		if(pnData.has("alert")){
			builder.setContentText(pnData.optString("alert"));
		}
		
		builder.setSmallIcon(getSmallIconId(context, intent))
		       .setLargeIcon(getLargeIcon(context, intent))
		       .setNumber(nextCount(pnTag))
		       .setContentIntent(contentIntent)
		       .setDeleteIntent(deleteIntent)
	           .setAutoCancel(true);
    
	    return builder.build();
	}
	
	private static JSONObject getPushData(Intent intent){
		JSONObject pnData = null;
		try {
            pnData = new JSONObject(intent.getStringExtra(PARSE_DATA_KEY));
        } catch (JSONException e) {
            Log.e(LOGTAG, "JSONException while parsing push data:", e);
        } finally{
        	return pnData;
        }
	}
	
	private static String getAppName(Context context){
		CharSequence appName = context.getPackageManager()
					                  .getApplicationLabel(context.getApplicationInfo());
		return (String)appName;
	}
	
	private static String getNotificationTag(Context context, Intent intent){
		return getPushData(intent).optString("title", getAppName(context));
	}
	
	private static String getNotificationTag(Context context, JSONObject pnData){
		return pnData.optString("title", getAppName(context));
	}
	
	private static int nextCount(String pnTag){
		try {
			MSG_COUNTS.put(pnTag, MSG_COUNTS.optInt(pnTag, 0) + 1);
        } catch (JSONException e) {
            Log.e(LOGTAG, "JSONException while computing next pn count for tag: [" + pnTag + "]", e);
        } finally{
        	return MSG_COUNTS.optInt(pnTag, 0);
        }
	}
	
	private static void resetCount(String pnTag){
		try {
			MSG_COUNTS.put(pnTag, 0);
        } catch (JSONException e) {
            Log.e(LOGTAG, "JSONException while resetting pn count for tag: [" + pnTag + "]", e);
        }
	}
}