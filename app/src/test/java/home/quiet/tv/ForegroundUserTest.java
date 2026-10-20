package home.quiet.tv;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundUserTest {
    @Test public void android6ResumedUserIsReadWithoutAssumingOwner() throws Exception {
        assertEquals(10,AdbController.parseForegroundUser("mResumedActivity: ActivityRecord{abc u10 home.quiet.tv/.SetupActivity t7}\n"));
    }
    @Test(expected=IOException.class) public void missingResumedActivityCannotAuthorizeWrites() throws Exception {
        AdbController.parseForegroundUser("mResumedActivity: null\nmStartedUserArray: [0, 10]");
    }
    @Test(expected=IOException.class) public void ambiguousResumedUsersCannotAuthorizeWrites() throws Exception {
        AdbController.parseForegroundUser("mResumedActivity: ActivityRecord{abc u0 example/.Main}\nmResumedActivity: ActivityRecord{def u10 example/.Main}");
    }
}
