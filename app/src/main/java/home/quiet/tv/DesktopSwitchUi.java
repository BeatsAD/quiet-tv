package home.quiet.tv;

import android.app.Dialog;

/** Legacy callers use the unified management sheet. */
final class DesktopSwitchUi {
    static Dialog show(AppManagementActivity ui,Runnable closed){
        DesktopGuardSheet sheet=new DesktopGuardSheet(ui,closed);sheet.show();return sheet.dialog();
    }
}
