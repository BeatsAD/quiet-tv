package home.quiet.tv;

/** Record the existing operands. No probing, admission decisions or error-message parsing. */
final class StorageEvidence {
    static String fields(String storage,String step,long incoming,long available,long reserve,long quota,long used,long maximum){
        return "storage="+storage+" step="+step+" incomingBytes="+incoming+" availableBytes="+available+" availability="+(available>0?"measured_positive":"zero_or_unknown")
            +" reserveBytes="+reserve+" quotaBytes="+(quota<0?"not_applicable":quota)+" usedBytes="+used+" maximumBytes="+maximum+" policy=existing_comparison";
    }
    static void record(String storage,String step,long incoming,long available,long reserve,long quota,long used,long maximum){DiagnosticLog.info("storage.check",fields(storage,step,incoming,available,reserve,quota,used,maximum));}
    private StorageEvidence(){}
}
