package org.example.community.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.FileStorageService;
import org.example.community.global.file.LocalFileStorageService;
import org.example.community.post.domain.Post;
import org.example.community.post.dto.request.PostCreateRequest;
import org.example.community.post.dto.response.*;
import org.example.community.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * final 필드나 @NotNull이 붙은 필드만 받는 생성자를 자동으로 만들어줌
 * public PostService(PostRepository postRepository, FileStorageService fileStorageService) {
 *     this.postRepository = postRepository;
 *     this.fileStorageService = fileStorageService;
 * }
 * 따라서 이런 코드를 직접 만들 필요가 없다.
 */
@Service
@RequiredArgsConstructor
public class PostService {


    private static final int DEFAULT_SIZE = 10;
    /**
     * 너무 많이 보내지 않게 최댓값 설정
     */
    private static final int MAX_SIZE = 50;

    private final FileStorageService fileStorageService;
    private final PostRepository postRepository;
    /**
     * ObjectMapper는 java 객체와 JSON 문자열을 변환하는 도구
     * import com.fasterxml.jackson.databind.ObjectMapper;
     * 이 부분이 읽어지지 않아 gradle에 직접 추가
     * 또한 Bean등록이 안되어서 config.JacksonConfig에 @Bean으로 ObjectMapper등록
     *
     */
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PostListResponse getPosts(String cursor, Integer size) {
        /**
         * 밑에 작성한 함수로서 null이면 DEFAULT_SIZE 반환
         * 1보다 작거나 MAX_SIZE보다 크면 BAD_REQUEST 예외 던짐
         */
        int validatedSize = validateSize(size);

        /**
         * 클라이언트가 보낸 cursor 문자열을 실제 게시글 id로 바꾸는 부분
         */
        Long decodedCursor = decodeCursor(cursor);
        /**
         * 여기서 DB에 게시글 목록 조회 요청
         * 여기서 validatedSize에 +1을 하면서 다음페이지가 있는지 확인함
         * 10개를 요청했지만 11개를 조회하면서 뒤에 더있다는것을 알림
         */
        List<PostSummaryResponse> fetchedPosts =
                postRepository.findPostsByCursor(decodedCursor, validatedSize + 1);
        /**
         * 나온 size로 hasNext 계산
         */
        boolean hasNext = fetchedPosts.size() > validatedSize;


        List<PostSummaryResponse> posts = fetchedPosts;
        /**
         * 총 11개를 가져왔다면 10개를 요청했기 때문에 마지막 1개를 자르는 역할을 함
         */
        if (hasNext) {
            posts = fetchedPosts.subList(0, validatedSize);
        }
        /**
         * 다음 페이지를 조회할 때 사용할 cursor 만들기
         * 응답으로 내려준 게시글 목록의 마지막 게시글 id를 기준으로 cursor 만듬
         * createNextCursor함수를 이용하여 인코딩된 nextCursor를 만드는 것
         */
        String nextCursor = createNextCursor(posts, hasNext);
        /**
         * 여기서 최종적으로 PostListResponse를 만들어서 반환한다.
         */
        return new PostListResponse(posts, nextCursor, hasNext);
    }

    private int validateSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return size;
    }

    /**
     * 클라이언트가 보낸 Base64 cursor를 실제 게시글 id로 바꾸는 역할
     * cursor가 null이면 findLatestPosts 실행
     * cursor가 있으면 findNextPosts 실행
     */
    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            /**
             * 인코딩된 cursor 문자열을 byte 배열로 디코딩
             */
            byte[] decodedBytes = Base64.getDecoder().decode(cursor);
            /**
             * byte배열을 UTF-8 기준으로 문자열을 바꿈
             */
            String json = new String(decodedBytes, StandardCharsets.UTF_8);

            /**
             * 바꾼 json을 Map으로 변환
             * 변환 결과는 { "postId" : 10} 이런식
             */
            Map<String, Object> cursorMap = objectMapper.readValue(json, Map.class);

            Object postId = cursorMap.get("postId");

            /**
             * 만약 커서에 postId가 없으면 BAD_REQUEST
             */
            if (postId == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            return Long.valueOf(postId.toString());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * 다음 페이지를 조회할 때 사용할 next_cursor를 만드는 역할
     */
    private String createNextCursor(List<PostSummaryResponse> posts, boolean hasNext) {
        if (!hasNext || posts.isEmpty()) {
            return null;
        }

        try {
            /**
             * 응답으로 내려줄 게시글 목록 중 마지막 게시글 반환
             */
            PostSummaryResponse lastPost = posts.get(posts.size() - 1);

            /**
             * 커서에 넣을 데이터를 Java Map으로 만듬
             */
            Map<String, Long> cursorMap = Map.of(
                    "postId", lastPost.getPostId()
            );
            /**
             * 이번엔 ObjectMapper를 사용하여 Java Map을 JSON 문자열로 바꿈
             */
            String json = objectMapper.writeValueAsString(cursorMap);
            /**
             * JSON 문자열을 Base64로 인코딩
             */
            return Base64.getEncoder()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public PostCreateResponse createPost(Long loginUserId,PostCreateRequest request, MultipartFile image) {

        /**
         * MultipartFile로 받은 이미지가 있으면 서버 폴더에 저장하고,
         * DB에는 실제 파일 자체가 아니라 저장된 파일 경로 문자열을 저장한다.
         * 이미지가 없으면 imageUrl은 null로 저장된다.
         */
        String imageUrl = fileStorageService.store(image,"posts");

        Post post = new Post(
                null,
                loginUserId,
                request.getTitle(),
                request.getContent(),
                imageUrl,
                null,
                null,
                null,
                null,
                null
        );

        return postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(Long postId, Long loginUserId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        /**
         * 게시글 상세 조회를 할 때 포스트가 존재하는 지 확인한 후 조회수 1 증가
         */
        postRepository.increaseViewCount(postId);

        return postRepository.findPostDetailById(postId, loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    @Transactional
    public PostUpdateResponse updatePost(
            Long postId,
            Long loginUserId,
            String title,
            String content,
            MultipartFile image
    ) {
        Long writerId = postRepository.findWriterIdByPostId(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!writerId.equals(loginUserId)) {
            throw new CustomException(ErrorCode.POST_FORBIDDEN);
        }

        String imageUrl = null;

        if (image != null && !image.isEmpty()) {
            imageUrl = fileStorageService.store(image, "posts");
        }

        postRepository.updatePost(postId, title, content, imageUrl);

        Post updatedPost = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        return new PostUpdateResponse(
                updatedPost.getId(),
                updatedPost.getTitle(),
                updatedPost.getContent(),
                updatedPost.getImageUrl(),
                updatedPost.getUpdatedAt()
        );
    }

    @Transactional
    public void deletePost(Long postId, Long loginUserId) {
        Long writerId = postRepository.findWriterIdByPostId(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!writerId.equals(loginUserId)) {
            throw new CustomException(ErrorCode.POST_DELETE_FORBIDDEN);
        }

        postRepository.deleteById(postId);
    }


}