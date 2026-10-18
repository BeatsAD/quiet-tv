package home.quiet.tv.fixture;
public final class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView label=new android.widget.TextView(this);label.setText("Quiet TV 专用验收应用，无账号及业务数据");label.setTextSize(28);setContentView(label);}
}
