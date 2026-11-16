package home.quiet.tv;

/** Read-only update decisions. Disk phases retain their existing wire values. */
final class UpdateState {
    enum Check { IDLE, CHECKING, FAILED }
    enum Download { IDLE, DOWNLOADING, SWITCHING, VERIFYING, READY, FAILED }
    enum Install {
        IDLE(""), PREPARING("preparing"), PREPARED("prepared"), FILE_DISPATCHED("file_dispatched"),
        COMMITTING("committing"), SUBMITTED("submitted"), CONFIRMATION("confirmation"),
        CONFIRMING("confirming"), AWAITING_VERSION("awaiting_version"), SUCCESS("success"),
        FAILED("failed"), CANCELLED("cancelled"), INTERRUPTED("interrupted"), UNKNOWN("unknown");
        final String wire;
        Install(String wire){this.wire=wire;}
        static Install read(String value){for(Install phase:values())if(phase.wire.equals(value))return phase;return UNKNOWN;}
    }
    final Check check;
    final Download download;
    final Install install,savedInstall;
    final String attempt;
    final int session;
    final boolean canDispatch,canConfirm,canRetryFile,needsReadback;
    UpdateState(boolean checking,String checkError,boolean downloading,boolean verifying,boolean switching,
            boolean ready,String downloadError,Install install,String saved,String attempt,int session,
            boolean installBusy,boolean confirmation){
        check=checking?Check.CHECKING:checkError.isEmpty()?Check.IDLE:Check.FAILED;
        // Byte completion is never readiness: verification also covers a restored cached APK.
        download=verifying?Download.VERIFYING:downloading?(switching?Download.SWITCHING:Download.DOWNLOADING)
            :ready?Download.READY:downloadError.isEmpty()?Download.IDLE:Download.FAILED;
        this.install=install;savedInstall=Install.read(saved);this.attempt=attempt;this.session=session;
        canDispatch=install==Install.PREPARED&&!installBusy;
        canConfirm=install==Install.CONFIRMATION&&confirmation;
        canRetryFile=!installBusy&&ready&&savedInstall==Install.FILE_DISPATCHED;
        needsReadback=savedInstall==Install.FILE_DISPATCHED||savedInstall==Install.UNKNOWN||session>=0||install==Install.AWAITING_VERSION
            ||install==Install.INTERRUPTED||install==Install.UNKNOWN;
    }
    String diagnostics(){return "check="+check+" download="+download+" install="+install.wire
        +" savedInstall="+savedInstall.wire+" attempt="+attempt+" session="+session
        +" canDispatch="+canDispatch+" canConfirm="+canConfirm+" canRetryFile="+canRetryFile+" needsReadback="+needsReadback;}
}
