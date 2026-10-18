package home.quiet.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetupPolicyTest {
    private static final String RULES="{\"schema\":1,\"devices\":[{\"brand\":\"Example\",\"fingerprint\":\"verified-build\",\"apps\":[{\"package\":\"example.demo\",\"recommendDisable\":true,\"actions\":[\"DISABLE\"]}],\"home\":{\"component\":\"example.home/.Main\",\"disableVerified\":true,\"uninstallVerified\":false}}]}";
    @Test public void unknownFirmwareCannotInheritBrandPermissions() throws Exception {
        SetupPresets rules=new SetupPresets(RULES,"Example","TV","different-build");
        assertFalse(rules.recommended("example.demo"));assertFalse(rules.allows("example.demo","DISABLE"));assertEquals("",rules.home());
    }
    @Test public void matchingFirmwareDoesNotGrantUnverifiedActions() throws Exception {
        SetupPresets rules=new SetupPresets(RULES,"example","TV","verified-build");
        assertTrue(rules.recommended("example.demo"));assertFalse(rules.allows("example.demo","UNINSTALL"));
        assertFalse(rules.recommended("example.unknown"));assertTrue(rules.homeAllows("disableVerified"));assertFalse(rules.homeAllows("uninstallVerified"));
    }
    @Test public void brandAndModelConstraintsAreEnforced() throws Exception {
        assertFalse(new SetupPresets(RULES,"Other","TV","verified-build").recommended("example.demo"));
        String scoped=RULES.replace("\"brand\":\"Example\"","\"brand\":\"Example\",\"model\":\"A\"");
        assertFalse(new SetupPresets(scoped,"Example","B","verified-build").recommended("example.demo"));
    }
    @Test(expected=org.json.JSONException.class) public void conflictingDeviceRulesFailClosed() throws Exception {
        String device=new org.json.JSONObject(RULES).getJSONArray("devices").getJSONObject(0).toString();
        new SetupPresets("{\"schema\":1,\"devices\":["+device+","+device+"]}","Example","TV","verified-build");
    }
    @Test public void homeParserRejectsErrorsAndResolverProse() {
        assertEquals("",HomeController.parseComponent("No activity found\nError: permission denied"));
        assertEquals("example.home/.Main",HomeController.parseComponent("priority=0\nexample.home/.Main\n"));
    }
    @Test public void cancelledSelectionCannotResumeAfterLateConnection() throws Exception {
        SetupOperations.Ticket confirmed=new SetupOperations.Ticket();confirmed.check();confirmed.cancel();
        assertFalse(confirmed.active());
        try{confirmed.check();fail("A late connection must not revive a cancelled selection");}catch(java.io.IOException expected){}
        SetupOperations.Ticket fresh=new SetupOperations.Ticket();assertTrue(fresh.active());assertFalse(confirmed.active());
    }
}
