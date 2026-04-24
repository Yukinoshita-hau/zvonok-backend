package com.zvonok.service.dto.event;

import com.zvonok.service.dto.UserShortDto;
import lombok.Data;

@Data
public class UserEvents {
    private UserEventsType type;
    private UserShortDto payload;
}
