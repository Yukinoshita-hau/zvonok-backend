package com.zvonok.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CodeCursorSyncRequestDto(
		@NotNull
		@Min(1)
		Integer lineNumber,

		@NotNull
		@Min(1)
		Integer column,

		@Min(1)
		Integer selectionStartLineNumber,

		@Min(1)
		Integer selectionStartColumn,

		@Min(1)
		Integer selectionEndLineNumber,

		@Min(1)
		Integer selectionEndColumn
) {
}
