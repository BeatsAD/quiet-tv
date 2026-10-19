package home.quiet.tv.homefixture;

public final class HomeActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView text=new android.widget.TextView(this);text.setText("仅用于专用模拟器的 HOME 验证组件");text.setGravity(android.view.Gravity.CENTER);setContentView(text);}
}
