package com.fixhome.soluciona;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.messaging.*;
import java.util.*;

public final class SolucionaMessagingService extends FirebaseMessagingService {
    static void channel(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel("soluciona_services","Pedidos, mensajes y pagos",NotificationManager.IMPORTANCE_HIGH));}
    static String deviceId(Context c){var prefs=c.getSharedPreferences("push",MODE_PRIVATE);String id=prefs.getString("deviceId","");if(id.isEmpty()){id=UUID.randomUUID().toString();prefs.edit().putString("deviceId",id).apply();}return id;}
    static void saveToken(Context c,String token){var u=FirebaseAuth.getInstance().getCurrentUser();if(u==null)return;String uid=u.getUid();Map<String,Object> data=new HashMap<>();data.put("token",token);data.put("enabled",true);data.put("platform","ANDROID");data.put("updatedAt",FieldValue.serverTimestamp());FirebaseFirestore.getInstance().collection("users").document(uid).collection("notification_devices").document(deviceId(c)).set(data);}
    static void logout(Context c,Runnable done){var u=FirebaseAuth.getInstance().getCurrentUser();if(u!=null)FirebaseFirestore.getInstance().collection("users").document(u.getUid()).collection("notification_devices").document(deviceId(c)).delete();FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(t->done.run());c.getSystemService(NotificationManager.class).cancelAll();}
    @Override public void onNewToken(String token){saveToken(this,token);}
    @Override public void onMessageReceived(RemoteMessage msg){var u=FirebaseAuth.getInstance().getCurrentUser();if(u==null||!u.getUid().equals(msg.getData().get("recipientUid")))return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;channel(this);
        Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);for(var pair:msg.getData().entrySet())i.putExtra(pair.getKey(),pair.getValue());String event=msg.getData().getOrDefault("eventId",UUID.randomUUID().toString());PendingIntent pi=PendingIntent.getActivity(this,event.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String body=msg.getNotification()==null?"Hay una actualización en Soluciona.":msg.getNotification().getBody();Notification n=new Notification.Builder(this,"soluciona_services").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Soluciona").setContentText(body).setContentIntent(pi).setAutoCancel(true).build();getSystemService(NotificationManager.class).notify(event.hashCode(),n);
    }
}
