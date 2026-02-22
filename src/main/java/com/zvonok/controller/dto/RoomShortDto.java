package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.RoomType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RoomShortDto {
    private final Long id;
    private final RoomType type;
}
