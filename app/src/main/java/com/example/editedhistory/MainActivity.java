package com.example.editedhistory;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.view.WindowManager;
import android.graphics.Color;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.content.SharedPreferences;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
  WebView web; ExecutorService worker=Executors.newSingleThreadExecutor();
  static final String KEY_ALIAS="edited_history_api_keys";
  @Override public void onCreate(Bundle b){super.onCreate(b);
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.addJavascriptInterface(new Bridge(),"AndroidBridge");web.setWebViewClient(new WebViewClient());
    setContentView(web);
    // Assets load under a private WebView pseudo-origin. No remote content is navigated.
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override public void onDestroy(){web.destroy();worker.shutdown();super.onDestroy();}
  SecretKey secret() throws Exception {
    KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
    if(!ks.containsAlias(KEY_ALIAS)){
      KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
      g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
       .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
    }
    return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS,null)).getSecretKey();
  }
  String encrypt(String plain) throws Exception {
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secret());
    byte[] iv=c.getIV(), encrypted=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    return Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
  }
  String decrypt(String value) throws Exception {
    String[] parts=value.split(":",2);if(parts.length!=2)return "";
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
    return new String(c.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
  }
  String keyFor(String provider){
    try{return decrypt(getPreferences(MODE_PRIVATE).getString("key_"+provider,""));}catch(Exception ex){return "";}
  }
  class Bridge {
    @JavascriptInterface public String getKey(String provider){return keyFor(provider).isEmpty()?"":"•••••••• (saved)";}
    @JavascriptInterface public void saveKey(String provider,String key){
      if(!validProvider(provider))return;
      try{if(key.contains("(saved)"))return;
        SharedPreferences.Editor edit=getPreferences(MODE_PRIVATE).edit();
        if(key.trim().isEmpty())edit.remove("key_"+provider);else edit.putString("key_"+provider,encrypt(key.trim()));edit.apply();
      }catch(Exception ex){} }
    @JavascriptInterface public void chat(int id,String raw){worker.submit(()->{
      try{String answer=callProvider(new JSONObject(raw));sendResult(id,true,answer);}
      catch(Exception ex){sendResult(id,false,ex.getMessage()==null?"Unknown API error":ex.getMessage());}
    });}
  }
  static boolean validProvider(String p){return p.equals("openai")||p.equals("gemini")||p.equals("anthropic")||p.equals("openrouter");}
  void sendResult(int id,boolean ok,String result){runOnUiThread(()->web.evaluateJavascript("window.nativeChatResult("+id+","+ok+","+JSONObject.quote(result)+")",null));}
  String callProvider(JSONObject request) throws Exception {
    String provider=request.optString("provider");if(!validProvider(provider))throw new Exception("Unknown provider");
    String key=keyFor(provider);if(key.isEmpty())throw new Exception("Set an API key in Settings first");
    String model=request.optString("model").trim();String url;
    switch(provider){
      case "openai":url="https://api.openai.com/v1/chat/completions";if(model.isEmpty())model="gpt-4.1-mini";break;
      case "gemini":url="https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";if(model.isEmpty())model="gemini-2.5-flash";break;
      case "anthropic":url="https://api.anthropic.com/v1/messages";if(model.isEmpty())model="claude-sonnet-4-5";break;
      default:url="https://openrouter.ai/api/v1/chat/completions";if(model.isEmpty())model="openai/gpt-4.1-mini";
    }
    JSONArray input=request.getJSONArray("messages");JSONArray messages=new JSONArray();
    for(int i=Math.max(0,input.length()-60);i<input.length();i++){
      JSONObject item=input.getJSONObject(i);String role=item.optString("role"),content=item.optString("content");
      if((role.equals("user")||role.equals("assistant"))&&!content.trim().isEmpty())messages.put(new JSONObject().put("role",role).put("content",content.length()>30000?content.substring(0,30000):content));
    }
    if(messages.length()==0)throw new Exception("No messages to send");
    JSONObject body=new JSONObject().put("model",model).put("messages",messages);
    if(provider.equals("anthropic"))body.put("max_tokens",2048);
    HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
    conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setConnectTimeout(25000);conn.setReadTimeout(120000);
    conn.setRequestProperty("Content-Type","application/json");
    if(provider.equals("anthropic")){conn.setRequestProperty("x-api-key",key);conn.setRequestProperty("anthropic-version","2023-06-01");}
    else conn.setRequestProperty("Authorization","Bearer "+key);
    try{
      byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
      try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
      int status=conn.getResponseCode();InputStream stream=status>=400?conn.getErrorStream():conn.getInputStream();
      if(stream==null)throw new Exception("Provider error "+status);
      byte[] data;try(InputStream in=stream){data=in.readNBytes(2_000_000);}
      JSONObject reply=new JSONObject(new String(data,StandardCharsets.UTF_8));
      if(status>=400)throw new Exception(reply.optJSONObject("error")!=null?reply.getJSONObject("error").optString("message","HTTP "+status):"HTTP "+status);
      if(provider.equals("anthropic")){
        JSONArray parts=reply.optJSONArray("content");StringBuilder text=new StringBuilder();
        if(parts!=null)for(int i=0;i<parts.length();i++){JSONObject part=parts.optJSONObject(i);if(part!=null&&part.optString("type").equals("text"))text.append(part.optString("text"));}
        if(text.length()==0)throw new Exception("Empty response");return text.toString();
      }
      JSONArray choices=reply.optJSONArray("choices");if(choices==null||choices.length()==0)throw new Exception("Empty response");
      String result=choices.getJSONObject(0).getJSONObject("message").optString("content");
      if(result.isEmpty())throw new Exception("Empty response");return result;
    }finally{conn.disconnect();}
  }
}
