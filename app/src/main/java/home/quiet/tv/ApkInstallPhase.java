package home.quiet.tv;

/** Internal negative markers and PackageInstaller results occupy different namespaces. */
enum ApkInstallPhase {
    PREPARING(-3), SUBMITTED(-2), CONFIRMATION(-1), CONFIRMING(-4),
    REPORTED_SUCCESS(0), REPORTED_FAILURE(1), UNKNOWN(Integer.MIN_VALUE);
    final int status;
    ApkInstallPhase(int status){this.status=status;}
    static ApkInstallPhase read(int status){
        if(status>0)return REPORTED_FAILURE;
        for(ApkInstallPhase phase:values())if(phase.status==status)return phase;
        return UNKNOWN;
    }
}
