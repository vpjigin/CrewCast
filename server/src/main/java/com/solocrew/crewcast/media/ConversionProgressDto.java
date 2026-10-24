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
        String startedByLabel,
        Integer queueAhead) {

    public static ConversionProgressDto from(AudioFixStatus st) {
        if (st == null) {
            return null;
        }
        return new ConversionProgressDto(
                st.status().name(),
                st.progress(),
                st.message(),
                st.speed(),
                st.etaSeconds(),
                st.startedBy() != null ? st.startedBy().name() : null,
                st.startedByLabel(),
                st.queueAhead());
    }
}
