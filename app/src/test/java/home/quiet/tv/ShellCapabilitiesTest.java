package home.quiet.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShellCapabilitiesTest {
    @Test public void legacyHelpDoesNotGrantUnscopedRestoreOrHome(){
        ShellCapabilities caps=new ShellCapabilities("pm enable [--user USER_ID] PACKAGE\npm disable-user [--user USER_ID] PACKAGE\npm uninstall [-k] [--user USER_ID] PACKAGE\npm install-existing PACKAGE\n", "Can't find service: package");
        assertTrue(caps.supports(AppOperations.Action.ENABLE));assertTrue(caps.supports(AppOperations.Action.DISABLE));
        assertTrue(caps.supports(AppOperations.Action.UNINSTALL));assertFalse(caps.supports(AppOperations.Action.RESTORE));
        assertEquals("",caps.home);assertEquals("",caps.resolve);
    }
    @Test public void modernMultilineSignaturesSelectCmdPackage(){
        String help="  resolve-activity [--brief]\n       [--user USER_ID] INTENT\n    Prints the activity.\n\n  set-home-activity [--user USER_ID] TARGET-COMPONENT\n  install-existing [--user USER_ID|all|current]\n       [--wait] PACKAGE\n";
        ShellCapabilities caps=new ShellCapabilities("",help);
        assertEquals("cmd package resolve-activity",caps.resolve);assertEquals("cmd package set-home-activity",caps.home);
        assertEquals("cmd package install-existing",caps.restore);
    }
    @Test public void adjacentCommandsCannotLendTheirUserOption(){
        ShellCapabilities caps=new ShellCapabilities("  enable PACKAGE\n  disable-user [--user USER_ID] PACKAGE\n  install-existing PACKAGE\n  list packages --user USER_ID\n", "");
        assertFalse(caps.enable);assertTrue(caps.disable);assertFalse(caps.supports(AppOperations.Action.RESTORE));
    }
    @Test public void cmdHelpCanProvideOperationsWhenPmHelpIsUnavailable(){
        ShellCapabilities caps=new ShellCapabilities("", "  enable [--user USER_ID] PACKAGE\n  disable-user [--user USER_ID] PACKAGE\n  uninstall [--user USER_ID] PACKAGE\n");
        assertTrue(caps.supports(AppOperations.Action.DISABLE));assertTrue(caps.supports(AppOperations.Action.UNINSTALL));
        assertEquals("cmd package disable-user",caps.disableCommand);assertEquals("cmd package enable",caps.enableCommand);
    }
    @Test public void unavailableHelpGrantsNoWrites(){
        for(AppOperations.Action action:AppOperations.Action.values())assertFalse(ShellCapabilities.NONE.supports(action));
    }
}
