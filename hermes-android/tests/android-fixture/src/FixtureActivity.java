package dev.hermesfixture.android;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import android.text.InputType;
/** Independent emulator fixture. Never included in Hermes release APK. */
public final class FixtureActivity extends Activity {
    int counter=0;TextView count;boolean child;
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)counter=b.getInt("counter",0);showMain();}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("counter",counter);}
    LinearLayout base(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,24,24,24);l.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets s=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(24+s.left,24+s.top,24+s.right,24+s.bottom);return i;});setContentView(l);return l;}
    TextView label(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(19);return t;}
    void showMain(){child=false;LinearLayout l=base();l.addView(label("Hermes accessibility fixture"));count=label("Counter: "+counter);count.setContentDescription("Counter: "+counter);l.addView(count);
        Button click=new Button(this);click.setText("Increment counter");click.setContentDescription("Increment counter");click.setOnClickListener(v->{counter++;count.setText("Counter: "+counter);count.setContentDescription("Counter: "+counter);});l.addView(click);
        EditText input=new EditText(this);input.setHint("Normal text field");input.setContentDescription("Normal text field");input.setSingleLine(true);l.addView(input);
        EditText password=new EditText(this);password.setSingleLine(true);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());password.setHint("Protected password field");password.setContentDescription("Protected password field");password.setText("never-expose-this-secret");l.addView(password);
        Button next=new Button(this);next.setText("Open child screen");next.setOnClickListener(v->showChild());l.addView(next);
        ScrollView scroll=new ScrollView(this);scroll.setContentDescription("Scrollable fixture list");LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);for(int n=0;n<50;n++){TextView row=label("Fixture row "+n);row.setPadding(8,18,8,18);rows.addView(row);}scroll.addView(rows);l.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    void showChild(){child=true;LinearLayout l=base();l.addView(label("Child screen visible"));Button back=new Button(this);back.setText("Return to fixture");back.setOnClickListener(v->showMain());l.addView(back);}
    @Override public void onBackPressed(){if(child)showMain();else super.onBackPressed();}
}
