package org.example.community.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.community.comment.entity.Comment;
import org.example.community.comment.dto.response.CommentCreateResponse;
import org.example.community.comment.dto.response.CommentListResponse;
import org.example.community.comment.dto.response.CommentSummaryResponse;
import org.example.community.comment.dto.response.CommentUpdateResponse;
import org.example.community.comment.repository.CommentRepository;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.post.entity.Post;
import org.example.community.post.repository.PostRepository;
import org.example.community.user.entity.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    /**
     * ObjectMapper는 java 객체와 JSON 문자열을 변환하는 도구
     * import com.fasterxml.jackson.databind.ObjectMapper;
     * 이 부분이 읽어지지 않아 gradle에 직접 추가
     * 또한 Bean등록이 안되어서 config.JacksonConfig에 @Bean으로 ObjectMapper등록
     *
     */
    private final ObjectMapper objectMapper;

    @Transactional
    public CommentCreateResponse createComment(
            Long postId,
            Long loginUserId,
            String content
    ) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.create(
                post,
                user,
                content
        );

        Comment savedComment = commentRepository.save(comment);

        /**
         * 이 부분에서 게시그르이 댓글 수를 증가시킨다.
         * 현재 트랜잭션 안에서 post도 영속상태이기 때문에
         * increaseCommentCount로 값을 변경하면
         * 변경 감지로 UPDATE SQL 실행
         */
        post.increaseCommentCount();

        return commentRepository.findCreateResponseById(
                        savedComment.getId(),
                        loginUserId
                )
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // 기존 JDBC는 Repistory에서 SQL로 조회했지만
    // 지금은 Impl에서 QueryDSL로 조회하는것
    @Transactional(readOnly = true)
    public CommentListResponse getComments(
            Long postId,
            Long loginUserId,
            String cursor,
            Integer size
    ) {
        postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        /**
         * 밑에 작성한 함수로서 null이면 DEFAULT_SIZE 반환
         * 1보다 작거나 MAX_SIZE보다 크면 BAD_REQUEST 예외 던짐
         */
        int validatedSize = validateSize(size);

        /**
         * 클라이언트가 보낸 cursor 문자열을 실제 댓글 id로 바꾸는 부분
         */
        Long decodedCursor = decodeCursor(cursor);

        /**
         * 여기서 DB에 댓글조회 요청
         * 여기서 validatedSize에 +1을 하면서 다음페이지가 있는지 확인함
         * 10개를 요청했지만 11개를 조회하면서 뒤에 더있다는것을 알림
         */
        List<CommentSummaryResponse> fetchedComments =
                commentRepository.findCommentsByCursor(
                        postId,
                        decodedCursor,
                        loginUserId,
                        validatedSize + 1
                );

        boolean hasNext = fetchedComments.size() > validatedSize;

        List<CommentSummaryResponse> comments = fetchedComments;

        // 11개를 조회하고 10개를 주어야하니 0부터 size까지 잘라서 comments에 저장
        if (hasNext) {
            comments = fetchedComments.subList(0, validatedSize);
        }

        /**
         * 다음 페이지를 조회할 때 사용할 cursor 만들기
         * 응답으로 내려준 댓글 목록의 마지막 댓글 id를 기준으로 cursor 만듬
         * createNextCursor함수를 이용하여 인코딩된 nextCursor를 만드는 것
         */
        String nextCursor = createNextCursor(comments, hasNext);

        return new CommentListResponse(
                comments,
                nextCursor,
                hasNext
        );
    }
    // 여기 Integer도 size값이 안들어올 경우를 생각하여
    // int가 아닌 Integer로 설정하였음
    private int validateSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return size;
    }

    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(cursor);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);

            Map<String, Object> cursorMap = objectMapper.readValue(json, Map.class);

            Object commentId = cursorMap.get("commentId");

            if (commentId == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            return Long.valueOf(commentId.toString());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private String createNextCursor(
            List<CommentSummaryResponse> comments,
            boolean hasNext
    ) {
        // hasNext가 false거나 온 comments가 없다면
        if (!hasNext || comments.isEmpty()) {
            return null;
        }

        try {
            //comments의 마지막 댓글을 내는 것
            CommentSummaryResponse lastComment = comments.get(comments.size() - 1);

            // 마지막 댓글의 idfmf Map으로 감사는 것
            // commentId = 20
            // map으로 감싸는건 나중에 확장성을 위해서. 최신순이라면 등록시간까지 넣어서 사용
            Map<String, Long> cursorMap = Map.of(
                    "commentId", lastComment.getCommentId()
            );

            // 위에서 만든 map을 JSON 문자열로 바꿔줌
            String json = objectMapper.writeValueAsString(cursorMap);

            return Base64.getEncoder()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public CommentUpdateResponse updateComment(
            Long postId,
            Long commentId,
            Long loginUserId,
            String content
    ) {
        postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));


        if (!comment.getPost().getId().equals(postId)) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getId().equals(loginUserId)) {
            throw new CustomException(ErrorCode.COMMENT_UPDATE_FORBIDDEN);
        }

        comment.updateContent(content);

        commentRepository.flush();

        return new CommentUpdateResponse(
                    comment.getId(),
                    comment.getContent(),
                    comment.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteComment(
            Long postId,
            Long commentId,
            Long loginUserId
    ) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));


        if (!comment.getPost().getId().equals(postId)) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getId().equals(loginUserId)) {
            throw new CustomException(ErrorCode.COMMENT_DELETE_FORBIDDEN);
        }

        commentRepository.delete(comment);

        post.decreaseCommentCount();
    }


}