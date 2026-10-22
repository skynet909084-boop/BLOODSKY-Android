package com.bloodsky.hmi;

/** Fail-closed gate for opening a Craft RTSP stream. */
public final class CraftRtspPlaybackPolicy {
    private CraftRtspPlaybackPolicy() { }

    public static CraftRtspConfig authorize(boolean operatorMode, boolean activityResumed,
                                             boolean explicitlyEnabled, boolean confirmed,
                                             String url) {
        if (!operatorMode || !activityResumed || !explicitlyEnabled || !confirmed) {
            throw new IllegalStateException("Запуск RTSP требует рабочего режима и явного подтверждения");
        }
        return CraftRtspConfig.parse(url);
    }
}
