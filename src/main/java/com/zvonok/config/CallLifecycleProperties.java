package com.zvonok.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Getter;

@Component
@Getter
public class CallLifecycleProperties {
    @Value("${call.lifecycle.invitation-seconds:60}")
    private long invitationSeconds = 60;
    @Value("${call.lifecycle.media-grace-seconds:90}")
    private long mediaGraceSeconds = 90;
    @Value("${call.lifecycle.initial-connect-seconds:120}")
    private long initialConnectSeconds = 120;
}
