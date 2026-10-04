package dev.chanho.hermes;
import android.content.*;
public final class ApprovalReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){String nonce=i.getStringExtra("nonce");if(nonce!=null)AgentRuntime.get(c).approvals.resolve(nonce,i.getBooleanExtra("allow",false));}
}
