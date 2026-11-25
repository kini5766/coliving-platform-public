package com.coliving.platform.post.dto;

import com.coliving.platform.common.entity.ContentStatus;
import com.coliving.platform.common.entity.SenderType;
import java.time.LocalDateTime;

/** 5-2 대댓글 */
public record ReplyDTO(
		Long commentId,
		Long authorId,
		SenderType authorType,
		String authorName,
		String authorRoomNumber,
		String content,
		ContentStatus status,
		boolean blocked,
		boolean isMine,
		LocalDateTime createdAt
) {
}
