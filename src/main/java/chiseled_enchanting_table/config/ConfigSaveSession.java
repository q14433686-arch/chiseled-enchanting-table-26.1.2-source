package chiseled_enchanting_table.config;

/** Request/acknowledgement lifecycle; no Minecraft, transport, rendering or disk side effects. */
public final class ConfigSaveSession {
    public enum Result { IGNORED, REJECTED, ACCEPTED, ACCEPTED_CLOSE }
    public static final long TIMEOUT_MS = 15_000;
    private long pendingId, startedAt, toastUntil;
    private boolean closeAfterSave, failed;
    public long pendingRequestId() { return pendingId; }
    public boolean failed() { return failed; }
    public long toastUntil() { return toastUntil; }

    public void begin(long requestId, boolean close, long now) {
        if (requestId <= 0 || pendingId != 0) throw new IllegalStateException("Invalid or overlapping save");
        pendingId = requestId;
        closeAfterSave = close;
        startedAt = now;
    }
    public void completed(boolean accepted, long now) {
        failed = !accepted;
        toastUntil = now + (accepted ? 2600 : 5000);
    }
    public Result acknowledge(long requestId, boolean accepted, long now) {
        if (pendingId == 0 || requestId != pendingId) return Result.IGNORED;
        pendingId = 0;
        completed(accepted, now);
        return !accepted ? Result.REJECTED : closeAfterSave ? Result.ACCEPTED_CLOSE : Result.ACCEPTED;
    }
    public boolean expire(long now) {
        if (pendingId == 0 || now - startedAt < TIMEOUT_MS) return false;
        pendingId = 0;
        completed(false, now);
        return true;
    }
}
