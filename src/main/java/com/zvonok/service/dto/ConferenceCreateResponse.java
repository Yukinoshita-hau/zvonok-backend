package com.zvonok.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConferenceCreateResponse {

    private Long conferenceId;

    private String code;

    private String joinUrl;

    private String livekitRoomName;

    private String serverUrl;

    private String token;
}
