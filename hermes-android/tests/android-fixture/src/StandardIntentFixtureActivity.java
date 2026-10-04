package dev.hermesfixture.android;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;
import org.json.*;
import java.io.*;
/** Public-marker local sink. Never sends, calls, browses, or creates an alarm. */
public final class StandardIntentFixtureActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);record(getIntent());}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);record(intent);}
 private void record(Intent intent){
  try{JSONObject row=new JSONObject().put("action",intent.getAction()).put("data",intent.getDataString()).put("type",intent.getType()).put("flags",intent.getFlags()).put("receivedAt",System.currentTimeMillis()).put("fixtureOnly",true).put("externalActionPerformed",false);
   JSONArray categories=new JSONArray();if(intent.getCategories()!=null)for(String c:intent.getCategories())categories.put(c);row.put("categories",categories);
   JSONObject extras=new JSONObject();Bundle b=intent.getExtras();if(b!=null)for(String key:b.keySet()){Object value=b.get(key);if(value instanceof String[])extras.put(key,new JSONArray(java.util.Arrays.asList((String[])value)));else if(value instanceof String||value instanceof Integer||value instanceof Boolean||value instanceof Long)extras.put(key,value);else extras.put(key,"[nonprimitive omitted]");}row.put("extras",extras);
   File f=new File(getExternalFilesDir(null),"intent-public-records.json");JSONArray records=new JSONArray();if(f.exists()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);}records=new JSONArray(bytes.toString("UTF-8"));}records.put(row);try(OutputStream out=new FileOutputStream(f)){out.write(records.toString(2).getBytes("UTF-8"));}
   TextView view=new TextView(this);view.setText("Local intent fixture received\n"+row.toString(2));view.setTextSize(14);setContentView(view);
  }catch(Exception e){TextView view=new TextView(this);view.setText("Fixture record error: "+e.getClass().getSimpleName());setContentView(view);}
 }
}
