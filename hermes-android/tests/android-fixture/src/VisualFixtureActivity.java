package dev.hermesfixture.android;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;
/** Emulator-only visual target; canvas challenge is deliberately absent from accessibility text. */
public final class VisualFixtureActivity extends Activity {
    int counter;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        if(getIntent().getBooleanExtra("secure",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,128,24,24);root.setBackgroundColor(Color.WHITE);root.setFocusableInTouchMode(true);setContentView(root);
        String challenge=getIntent().getStringExtra("challenge");final String badge=challenge==null?"VISION FIXTURE 008":challenge;
        View pixels=new View(this){final Paint paint=new Paint();@Override protected void onDraw(Canvas c){int w=getWidth(),h=getHeight();int[] colors={Color.rgb(231,35,61),Color.rgb(32,190,105),Color.rgb(38,99,224)};for(int i=0;i<3;i++){paint.setColor(colors[i]);c.drawRect(i*w/3f,0,(i+1)*w/3f,h,paint);}paint.setColor(Color.WHITE);paint.setTextSize(40);c.drawText(badge,20,h/2f,paint);}};
        pixels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(pixels,new LinearLayout.LayoutParams(-1,180));
        TextView count=new TextView(this);count.setText("Visual counter: 0");count.setTextSize(22);root.addView(count);
        Button increment=new Button(this);increment.setText("Visual increment");increment.setContentDescription("Visual increment");increment.setOnClickListener(v->{counter++;count.setText("Visual counter: "+counter);});root.addView(increment);
        EditText normal=new EditText(this);normal.setContentDescription("Visual normal field");normal.setHint("Visible normal field");normal.setSingleLine(true);root.addView(normal);
        if(!getIntent().getBooleanExtra("omitPassword",false)){EditText secret=new EditText(this);secret.setSingleLine(true);secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);secret.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());secret.setContentDescription("Visual protected password");secret.setText("VISUAL_SECRET_008_DO_NOT_EXPOSE");root.addView(secret);}
        TextView hint=new TextView(this);hint.setText("Independent visual fixture. Canvas colors and badge require real pixels.");root.addView(hint);
        if(getIntent().getBooleanExtra("controls",false)){
            TextView rangeState=new TextView(this);rangeState.setText("Fixture range: 25");root.addView(rangeState);
            SeekBar range=new SeekBar(this);range.setContentDescription("Fixture adjustable range");range.setMin(0);range.setMax(100);range.setProgress(25);range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean fromUser){rangeState.setText("Fixture range: "+p);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});root.addView(range);
            TextView longState=new TextView(this);longState.setText("Fixture long clicks: 0");root.addView(longState);
            Button hold=new Button(this);hold.setText("Fixture hold button");hold.setContentDescription("Fixture hold button");final int[] holds={0};hold.setOnLongClickListener(v->{longState.setText("Fixture long clicks: "+(++holds[0]));return true;});root.addView(hold);
        }
        root.requestFocus();
    }
}
