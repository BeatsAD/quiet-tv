package home.quiet.tv;

/** Legacy startup entry uses the same retained settings panels and navigation. */
public final class WatchTvActivity extends AppManagementActivity {
    @Override int initialPage(){return SettingsPages.STARTUP;}
}
