package com.zvonok.controller.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageReadersDto {
	List<Long> messageIds;
}
