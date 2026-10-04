package dev.chanho.hermes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Release-safe developer read endpoint. Never opens a caller-supplied filesystem path. */
public final class DiagnosticsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.chanho.hermes.diagnostics";
    public static final String EVENTS_URI = "content://" + AUTHORITY + "/events";
    @Override public boolean onCreate() { return true; }
    private void authorize(Uri uri) {
        int caller=Binder.getCallingUid();
        if(caller!=Process.myUid() && caller!=0 && caller!=2000) throw new SecurityException("diagnostics caller denied");
        if(uri==null || !EVENTS_URI.equals(uri.toString())) throw new SecurityException("diagnostics path denied");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        authorize(uri);
        if(!"r".equals(mode)) throw new SecurityException("diagnostics is read only");
        if(getContext()==null) throw new FileNotFoundException("diagnostics unavailable");
        final byte[] report;
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); JSONArray rows=Diagnostics.snapshotForExport(getContext());
            for(int i=0;i<Math.min(rows.length(),Diagnostics.MAX_RECORDS);i++) {
                byte[] line=(rows.getJSONObject(i).toString()+"\n").getBytes(StandardCharsets.UTF_8);
                if(bytes.size()+line.length>Diagnostics.MAX_BYTES) break;
                bytes.write(line);
            }
            report=bytes.toByteArray();
            final ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            Thread writer=new Thread(() -> {
                try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) { out.write(report); }
                catch(Exception ignored) { /* Reader may close early; no secondary error loop. */ }
            },"hermes-diagnostics-read");
            writer.setDaemon(true); writer.start(); return pipe[0];
        } catch(Exception failure) { throw new FileNotFoundException("diagnostics unavailable"); }
    }
    @Override public String getType(Uri uri) { authorize(uri); return "application/x-ndjson"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        authorize(uri); throw new UnsupportedOperationException("diagnostics supports stream read only");
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new SecurityException("diagnostics is read only"); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new SecurityException("diagnostics is read only"); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new SecurityException("diagnostics is read only"); }
}
