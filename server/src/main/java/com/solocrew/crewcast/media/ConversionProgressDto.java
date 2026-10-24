package com.solocrew.crewcast.media;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversionProgressDto(
        String status,
        int progress,
        String message,
        Double speed,
        Integer etaSeconds,
        String startedBy,
        Integer queueAhead) {

    public static ConversionProgressDto from(AudioFixStatus st, String requestClientId) {
        if (st == null) {
            return null;
        }
        return new ConversionProgressDto(
                st.status().name(),
                st.progress(),
                st.message(),
                st.speed(),
                st.etaSeconds(),
                ConversionPublicStartedBy.forClient(st, requestClientId),
                st.queueAhead());
    }
}
