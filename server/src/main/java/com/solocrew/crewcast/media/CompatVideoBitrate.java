package com.solocrew.crewcast.media;

import java.util.Locale;

final class CompatVideoBitrate {

    static final String CACHE_POLICY_TAG = "cap=source-ratio-v1";

    private CompatVideoBitrate() {
    }

    static int targetKbps(ProbedMedia media, int maxHeight, long sourceFormatBitRateBps) {
        int outHeight = outputHeight(media, maxHeight);
        int tier = tierKbps(outHeight);
        int floor = minKbpsFloor(outHeight);
        int capped = tier;
        if (sourceFormatBitRateBps > 0) {
            int sourceKbps = (int) Math.min(Integer.MAX_VALUE, sourceFormatBitRateBps / 1000);
            double ratio = sourceBitrateCapRatio(media);
            capped = Math.min(tier, (int) Math.round(sourceKbps * ratio));
        }
        return Math.max(floor, capped);
    }

    static double sourceBitrateCapRatio(ProbedMedia media) {
        String codec = media.mainVideoCodec() == null ? "" : media.mainVideoCodec().toLowerCase(Locale.ROOT);
        if (MediaProbe.isHevcCodec(codec) || codec.contains("av1") || codec.contains("vp9")) {
            return 1.6;
        }
        if (codec.equals("h264") || codec.equals("avc1") || codec.contains("mpeg4") || codec.equals("mp4v")) {
            return 1.0;
        }
        return 1.6;
    }

    private static int minKbpsFloor(int outHeight) {
        if (outHeight <= 480) {
            return 600;
        }
        if (outHeight <= 720) {
            return 1000;
        }
        return 1000;
    }

    private static int tierKbps(int outHeight) {
        if (outHeight <= 0) {
            return 3000;
        }
        if (outHeight <= 480) {
            return 1500;
        }
        if (outHeight <= 720) {
            return 3000;
        }
        return 5000;
    }

    private static int outputHeight(ProbedMedia media, int maxHeight) {
        VideoFormat v = media.video();
        if (v == null || v.height() <= 0) {
            return maxHeight >= 1080 ? 1080 : 720;
        }
        int cap = maxHeight >= 1080 ? 1080 : 720;
        return Math.min(v.height(), cap);
    }
}
