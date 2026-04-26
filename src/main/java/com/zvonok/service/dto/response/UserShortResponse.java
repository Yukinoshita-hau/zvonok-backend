package com.zvonok.service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserShortResponse {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String aboutMe;
}
