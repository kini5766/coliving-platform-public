package com.coliving.platform.post.service;

import com.coliving.platform.common.auth.ResidentGuard;
import com.coliving.platform.client.PropertyClient;
import com.coliving.platform.common.auth.ResidentGuard.Resident;
import com.coliving.platform.common.entity.SenderType;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import com.coliving.platform.notification.entity.NotificationTarget;
import com.coliving.platform.notification.entity.NotificationType;
import com.coliving.platform.notification.service.NotificationService;
import com.coliving.platform.post.dto.CommentCreateResponseDTO;
import com.coliving.platform.post.dto.CommentRequestDTO;
import com.coliving.platform.post.dto.CommentUpdateResponseDTO;
import com.coliving.platform.post.entity.CommentEntity;
import com.coliving.platform.post.entity.PostEntity;
import com.coliving.platform.post.repository.CommentRepository;
import com.coliving.platform.post.repository.PostRepository;
import com.coliving.platform.safety.service.BlockQueryService;
import com.coliving.platform.safety.service.SanctionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 게시판 — 댓글 · 답글 (API 5-6 ~ 5-8) */
@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final PostService postService;
	private final ResidentGuard residentGuard;
	private final SanctionGuard sanctionGuard;
	private final BlockQueryService blockQueryService;
	private final NotificationService notificationService;
	private final PropertyClient propertyClient;

	/**
	 * 5-6 댓글 · 답글 작성 (답글은 1단계까지)
	 * 입주자: 내 건물 글만, 제재 중이면 403 / 관리자(매니저): 어느 건물 글이든 (TODO 연동 1-3: 담당 건물만), 제재·차단과 무관
	 */
	@Transactional
	public CommentCreateResponseDTO create(Long userId, Long postId, CommentRequestDTO request) {
		boolean admin = residentGuard.isAdmin(userId);
		PostEntity post = postService.findActivePost(postId);
		String writerName;
		if (admin) {
			writerName = PostService.managerName(propertyClient.getBuildingName(post.getBuildingId()));
		} else {
			Resident me = residentGuard.requireResident(userId);
			sanctionGuard.checkCanWrite(userId);
			residentGuard.requireSameBuilding(me, post.getBuildingId());
			writerName = me.name();
		}

		CommentEntity parent = null;
		if (request.parentCommentId() != null) {
			parent = commentRepository.findById(request.parentCommentId())
					.filter(comment -> comment.getPost().getId().equals(postId) && comment.isActive())
					.orElseThrow(() -> new CommunityException(ErrorCode.COMMENT_NOT_FOUND));
			if (parent.isReply()) {
				throw new CommunityException(ErrorCode.REPLY_DEPTH_EXCEEDED);
			}
		}

		CommentEntity saved = commentRepository.save(CommentEntity.create(post, parent, userId,
				admin ? SenderType.ADMIN : SenderType.USER, request.content()));
		postRepository.addCommentCount(postId, 1);
		notifyAuthor(userId, writerName, admin, post, parent, saved.getContent());

		return new CommentCreateResponseDTO(saved.getId(), parent != null ? parent.getId() : null, saved.getCreatedAt());
	}

	/** 5-7 댓글 수정 (작성자 본인) */
	@Transactional
	public CommentUpdateResponseDTO update(Long userId, Long commentId, String content) {
		CommentEntity comment = findOwnActiveComment(userId, commentId);
		if (!comment.isByAdmin()) {
			sanctionGuard.checkCanWrite(userId);
		}
		comment.updateContent(content);
		commentRepository.saveAndFlush(comment);
		return new CommentUpdateResponseDTO(comment.getId(), comment.getUpdatedAt());
	}

	/** 5-8 댓글 삭제 (소프트 삭제, 달린 답글은 유지) */
	@Transactional
	public void delete(Long userId, Long commentId) {
		CommentEntity comment = findOwnActiveComment(userId, commentId);
		comment.delete();
		postRepository.addCommentCount(comment.getPost().getId(), -1);
	}

	private CommentEntity findOwnActiveComment(Long userId, Long commentId) {
		CommentEntity comment = commentRepository.findById(commentId)
				.filter(CommentEntity::isActive)
				.orElseThrow(() -> new CommunityException(ErrorCode.COMMENT_NOT_FOUND));
		if (!comment.isWrittenBy(userId)) {
			throw new CommunityException(ErrorCode.NOT_OWNER, "본인이 작성한 댓글만 수정·삭제할 수 있어요.");
		}
		return comment;
	}

	/**
	 * 글쓴이(답글이면 원댓글 작성자)에게 알림 "김민서: 댓글 미리보기"
	 * 본인 글이면 생략, 입주자가 쓴 댓글인데 상대가 나를 차단했으면 생략
	 * 받는 사람이 매니저(공지 글·매니저 댓글)면 관리자 알림으로
	 */
	private void notifyAuthor(Long writerId, String writerName, boolean writerIsAdmin, PostEntity post,
			CommentEntity parent, String content) {
		Long receiverId = parent != null ? parent.getAuthorId() : post.getAuthorId();
		boolean receiverIsAdmin = parent != null ? parent.isByAdmin() : post.getAuthorType() == SenderType.ADMIN;
		if (receiverId.equals(writerId)
				|| (!writerIsAdmin && !receiverIsAdmin && blockQueryService.isBlockedBy(writerId, receiverId))) {
			return;
		}
		NotificationType type = parent != null ? NotificationType.REPLY : NotificationType.COMMENT;
		String title = parent != null ? "새 답글" : "새 댓글";
		String message = writerName + ": " + NotificationService.preview(content);
		if (receiverIsAdmin) {
			notificationService.notifyAdmin(receiverId, type, title, message, NotificationTarget.POST, post.getId());
		} else {
			notificationService.notifyUser(receiverId, type, title, message, NotificationTarget.POST, post.getId());
		}
	}
}
