package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.viewers.ViewerRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public AccessTokenFilter accessTokenFilter(AccessSession accessSession, SettingsStore settingsStore,
                                               SharingState sharingState) {
        return new AccessTokenFilter(accessSession, settingsStore, sharingState);
    }

    @Bean
    public ViewerTrackingFilter viewerTrackingFilter(ShareRegistry shareRegistry, ViewerRegistry viewerRegistry,
                                                     SharingService sharingService) {
        return new ViewerTrackingFilter(shareRegistry, viewerRegistry, sharingService);
    }

    @Bean
    public FilterRegistrationBean<AccessTokenFilter> accessTokenFilterRegistration(AccessTokenFilter filter) {
        FilterRegistrationBean<AccessTokenFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ViewerTrackingFilter> viewerTrackingFilterRegistration(ViewerTrackingFilter filter) {
        FilterRegistrationBean<ViewerTrackingFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        bean.addUrlPatterns("/stream/*");
        return bean;
    }
}
