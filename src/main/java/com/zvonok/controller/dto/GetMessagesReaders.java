package com.zvonok.controller.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GetMessagesReaders {
	Long messageId;
	List<String> readers;
}
