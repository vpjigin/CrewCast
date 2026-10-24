package com.solocrew.crewcast.media;

public final class ConversionPublicStartedBy {

    public static final String HOST = "HOST";
    public static final String VIEWER = "VIEWER";
    public static final String SELF = "SELF";

    private ConversionPublicStartedBy() {
    }

    public static String forClient(AudioFixStatus status, String requestClientId) {
        if (status == null || status.startedBy() == null) {
            return null;
        }
        return switch (status.startedBy()) {
            case HOST -> HOST;
            case VIEWER -> {
                String norm = ConversionClientIds.normalize(requestClientId);
                if (norm != null
                        && status.startedByClientId() != null
                        && norm.equals(status.startedByClientId())) {
                    yield SELF;
                }
                yield VIEWER;
            }
        };
    }
}
