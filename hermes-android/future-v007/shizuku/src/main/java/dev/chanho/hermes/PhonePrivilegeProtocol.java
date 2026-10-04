package dev.chanho.hermes;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import org.json.JSONObject;

/** Typed Binder calls. No arbitrary command string is part of this protocol. */
final class PhonePrivilegeProtocol {
    static final String DESCRIPTOR = "dev.chanho.hermes.IPhonePrivilege";
    static final int STATUS=1, CONFIGURE=2, WIFI=3, PROCESSES=4, FORCE_STOP=5,
            READ_SYSTEM_SETTING=6, CANCEL=7, WRITE_SYSTEM_SETTING=8, DESTROY=16777115;

    static JSONObject call(IBinder binder, int operation, JSONObject arguments) throws Exception {
        if (binder == null || !binder.isBinderAlive()) throw new RemoteException("기기 제어 연결이 종료되었습니다.");
        Parcel data=Parcel.obtain(), reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(arguments == null ? "{}" : arguments.toString());
            if (!binder.transact(operation, data, reply, 0)) throw new RemoteException("지원하지 않는 기기 제어 요청입니다.");
            reply.readException();
            String text=reply.readString();
            if (text == null) throw new RemoteException("기기 제어 응답이 없습니다.");
            JSONObject result=new JSONObject(text);
            if (!result.optBoolean("ok", false)) throw new SecurityException(result.optString("error", "기기 제어 요청이 거절되었습니다."));
            return result;
        } finally { reply.recycle(); data.recycle(); }
    }
    static JSONObject object(Object... fields) {
        JSONObject result=new JSONObject();
        try { for(int i=0;i<fields.length;i+=2) result.put(String.valueOf(fields[i]),fields[i+1]); }
        catch(Exception impossible) { throw new IllegalArgumentException(impossible); }
        return result;
    }
}
