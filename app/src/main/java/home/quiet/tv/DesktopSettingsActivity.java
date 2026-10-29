package home.quiet.tv;

/** The public desktop-settings entry opens the same seven-tab settings window. */
public final class DesktopSettingsActivity extends AppManagementActivity {
    static final String SECTION="section";
    @Override int initialPage(){
        if(getIntent().hasExtra("management-page"))return super.initialPage();
        return "weather".equals(getIntent().getStringExtra(SECTION))?SettingsPages.WEATHER:SettingsPages.DESKTOP;
    }
}
