package com.fixhome.soluciona;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.ImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class HighPriorityBridge {
    static final int PICK_IMAGE = 9130;
    private final MainActivity activity;
    private final WebView web;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ListenerRegistration identityListener, serviceListener;
    private String pendingType, pendingRequest, pendingUid;
    private volatile boolean uploading;
    private final FirebaseAuth.AuthStateListener authListener;
    HighPriorityBridge(MainActivity activity, WebView web) {
        this.activity=activity;this.web=web;
        SolucionaMessagingService.channel(activity);
        authListener=a->{stopListeners();};
        auth.addAuthStateListener(authListener);
    }
    private void emit(String kind, boolean ok, JSONObject data, String uid) {
        try {data.put("kind",kind);data.put("ownerUid",uid==null?"":uid);}catch(Exception ignored){}
        activity.runOnUiThread(()->{if(!activity.isDestroyed())web.evaluateJavascript("window.solucionaPriorityEvent&&window.solucionaPriorityEvent("+ok+","+data+");",null);});
    }
    private void error(String kind, String message, String uid) {JSONObject d=new JSONObject();try{d.put("message",message);}catch(Exception ignored){}emit(kind,false,d,uid);}
    @JavascriptInterface public void subscribeIdentity() {
        FirebaseUser user=auth.getCurrentUser();if(user==null)return;String uid=user.getUid();
        activity.runOnUiThread(()->{if(identityListener!=null)identityListener.remove();identityListener=db.collection("identity_submissions").document(uid).addSnapshotListener((doc,e)->{
            if(e!=null){error("identity","No se pudo consultar la documentación. Reintentá al recuperar conexión.",uid);return;}
            JSONObject d=new JSONObject();try{d.put("status",doc.exists()?doc.getString("status"):"NOT_UPLOADED");d.put("reviewNote",doc.exists()?doc.getString("reviewNote"):"");d.put("front",doc.exists()&&doc.contains("front"));d.put("back",doc.exists()&&doc.contains("back"));}catch(Exception ignored){}emit("identity",true,d,uid);
        });});
    }
    @JavascriptInterface public void subscribeService(String requestId) {
        FirebaseUser user=auth.getCurrentUser();if(user==null||!validId(requestId))return;String uid=user.getUid();
        activity.runOnUiThread(()->{if(serviceListener!=null)serviceListener.remove();serviceListener=db.collection("service_requests").document(requestId).addSnapshotListener((doc,e)->{
            if(e!=null||!doc.exists()){error("service","No se pudo consultar el pedido.",uid);return;}
            JSONObject d=new JSONObject(doc.getData());try{d.put("id",doc.getId());}catch(Exception ignored){}emit("service",true,d,uid);
        });});
        db.collection("service_requests").document(requestId).collection("attachments").orderBy("createdAt").limit(6).get().addOnSuccessListener(activity,snap->{JSONArray arr=new JSONArray();for(var doc:snap.getDocuments()){JSONObject d=new JSONObject(doc.getData());try{d.put("id",doc.getId());}catch(Exception ignored){}arr.put(d);}JSONObject data=new JSONObject();try{data.put("requestId",requestId);data.put("items",arr);}catch(Exception ignored){}emit("photos",true,data,uid);}).addOnFailureListener(activity,e->error("photos","No se pudieron cargar las fotos.",uid));
    }
    @JavascriptInterface public void stopService() {activity.runOnUiThread(()->{if(serviceListener!=null){serviceListener.remove();serviceListener=null;}});}
    @JavascriptInterface public void stopIdentity() {activity.runOnUiThread(()->{if(identityListener!=null){identityListener.remove();identityListener=null;}});}
    private void stopListeners(){if(identityListener!=null)identityListener.remove();if(serviceListener!=null)serviceListener.remove();identityListener=null;serviceListener=null;}
    @JavascriptInterface public void chooseDni(String type, boolean consent) {
        if(!consent||!Arrays.asList("DNI_FRONT","DNI_BACK").contains(type)){error("upload","Aceptá el uso de tus documentos para revisión antes de continuar.",currentUid());return;}choose(type,"");
    }
    @JavascriptInterface public void chooseProblemPhoto(String requestId) {if(validId(requestId))choose("REQUEST_PHOTO",requestId);}
    private String currentUid(){FirebaseUser u=auth.getCurrentUser();return u==null?"":u.getUid();}
    private static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,128}");}
    private void choose(String type,String requestId){activity.runOnUiThread(()->{
        if(uploading||pendingType!=null){error("upload","Ya hay una carga en curso.",currentUid());return;}
        if(auth.getCurrentUser()==null||!auth.getCurrentUser().isEmailVerified()||BuildConfig.DOCUMENTS_API_URL.isBlank()){error("upload","Ingresá con correo verificado y configurá el servidor de archivos.",currentUid());return;}
        pendingType=type;pendingRequest=requestId;pendingUid=currentUid();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});activity.startActivityForResult(i,PICK_IMAGE);
    });}
    void imageResult(Uri uri){final String type=pendingType,requestId=pendingRequest,pickedUid=pendingUid;pendingType=null;pendingRequest=null;pendingUid=null;if(uri==null){error("upload","Selección cancelada.",currentUid());return;}
        FirebaseUser u=auth.getCurrentUser();if(u==null||type==null||!u.getUid().equals(pickedUid))return;final String uid=u.getUid();uploading=true;
        u.getIdToken(false).addOnSuccessListener(activity,result->{io.execute(()->upload(uri,type,requestId,uid,result.getToken()));}).addOnFailureListener(activity,e->{uploading=false;error("upload","No se pudo renovar la sesión.",uid);});
    }
    private void upload(Uri uri,String type,String requestId,String uid,String token){HttpURLConnection c=null;try{
        String mime=activity.getContentResolver().getType(uri);if(!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime==null?"":mime))throw new IOException("Elegí una imagen JPG, PNG o WebP.");
        String boundary="Soluciona"+UUID.randomUUID().toString().replace("-","");
        c=(HttpURLConnection)new URL(BuildConfig.DOCUMENTS_API_URL.replaceAll("/+$","")+(type.equals("REQUEST_PHOTO")?"/v1/request-photos":"/v1/identity-documents")).openConnection();c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setChunkedStreamingMode(8192);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        Map<String,String> formFields=new LinkedHashMap<>();formFields.put("type",type);formFields.put("requestId",requestId);formFields.put("consent","true");
        try(DataOutputStream out=new DataOutputStream(c.getOutputStream())){for(var pair:formFields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+pair.getKey()+"\"\r\n\r\n"+pair.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"imagen\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try(InputStream in=activity.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("No se pudo leer la imagen.");byte[] buf=new byte[8192];long total=0;int n;while((n=in.read(buf))!=-1){total+=n;if(total>10L*1024*1024)throw new IOException("La imagen supera 10 MB.");out.write(buf,0,n);}}
            out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));}
        if(c.getResponseCode()!=201)throw new IOException("La carga no se completó ("+c.getResponseCode()+"). Revisá conexión, formato y permisos; las fotos del problema se agregan antes de aceptar el pedido.");
        JSONObject d=new JSONObject();d.put("requestId",requestId);d.put("type",type);emit("upload",true,d,uid);
    }catch(Exception e){error("upload",e.getMessage()==null?"No se pudo subir. Reintentá.":e.getMessage(),uid);}finally{uploading=false;if(c!=null)c.disconnect();}}
    @JavascriptInterface public void offerBudget(String json){call("offerBudget",json);}
    @JavascriptInterface public void respondBudget(String json){call("respondBudget",json);}
    private void call(String name,String json){String uid=currentUid();try{JSONObject o=new JSONObject(json);Map<String,Object> map=new HashMap<>();for(Iterator<String> it=o.keys();it.hasNext();){String key=it.next();map.put(key,o.get(key));}
        FirebaseFunctions.getInstance("southamerica-east1").getHttpsCallable(name).call(map).addOnSuccessListener(activity,r->emit("budget",true,new JSONObject(),uid)).addOnFailureListener(activity,e->error("budget",e.getMessage(),uid));
    }catch(Exception e){error("budget","Revisá los datos del presupuesto.",uid);}}
    @JavascriptInterface public void enableNotifications(){activity.runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9131);else registerNotifications();});}
    @JavascriptInterface public void registerNotifications(){FirebaseMessaging.getInstance().getToken().addOnSuccessListener(activity,token->{SolucionaMessagingService.saveToken(activity,token);}).addOnFailureListener(activity,e->error("notifications","No se pudieron activar los avisos.",currentUid()));}
    @JavascriptInterface public void viewPhoto(String key){FirebaseUser u=auth.getCurrentUser();if(u==null||!key.startsWith("request-photos/"))return;String uid=u.getUid();u.getIdToken(false).addOnSuccessListener(activity,t->io.execute(()->{HttpURLConnection c=null;try{
        c=(HttpURLConnection)new URL(BuildConfig.DOCUMENTS_API_URL.replaceAll("/+$","")+"/v1/document?key="+URLEncoder.encode(key,"UTF-8")).openConnection();c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+t.getToken());c.setConnectTimeout(20000);c.setReadTimeout(30000);if(c.getResponseCode()!=200)throw new IOException();
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>10*1024*1024)throw new IOException();out.write(buf,0,n);}}
        byte[] bytes=out.toByteArray();BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);opt.inSampleSize=1;while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>1600)opt.inSampleSize*=2;opt.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);if(bitmap==null)throw new IOException();
        activity.runOnUiThread(()->{if(!uid.equals(currentUid())||activity.isDestroyed())return;ImageView image=new ImageView(activity);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);new AlertDialog.Builder(activity).setTitle("Foto del problema").setView(image).setPositiveButton("Cerrar",(d,w)->{}).show();});
    }catch(Exception e){error("photos","No se pudo abrir la foto.",uid);}finally{if(c!=null)c.disconnect();}}));}
    @JavascriptInterface public String consumeNotification(){
        Intent i=activity.getIntent();String uid=currentUid();if(i==null||uid.isEmpty()||!uid.equals(i.getStringExtra("recipientUid")))return "{}";JSONObject data=new JSONObject();try{data.put("requestId",i.getStringExtra("requestId"));data.put("kind",i.getStringExtra("kind"));}catch(Exception ignored){}i.removeExtra("recipientUid");return data.toString();
    }
    void close(){auth.removeAuthStateListener(authListener);stopListeners();io.shutdownNow();}
}
