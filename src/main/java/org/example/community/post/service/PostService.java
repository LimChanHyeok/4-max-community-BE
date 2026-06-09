package org.example.community.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.example.community.image.repository.ImageRepository;
import org.example.community.post.dto.request.PostUpdateRequest;
import org.example.community.post.entity.Post;
import org.example.community.post.dto.request.PostCreateRequest;
import org.example.community.post.dto.response.*;
import org.example.community.post.repository.PostRepository;
import org.example.community.user.entity.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
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
    public PostCreateResponse createPost(Long loginUserId,PostCreateRequest request) {

        /**
         * 게시글 작성자는 로그인한 사용자이므로 loginUserId로 User 엔티티를 조회한다.
         *
         * JPA의 Post 엔티티는 userId 값만 가지는 것이 아니라
         * User 엔티티와 @ManyToOne 관계를 맺고 있기 때문에
         * 게시글 생성 시 User 객체가 필요하다.
         */
        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        /**
         * MultipartFile로 받은 이미지가 있으면 서버 폴더에 저장하고,
         * DB에는 실제 파일 자체가 아니라 저장된 파일 경로 문자열을 저장한다.
         * 이미지가 없으면 imageUrl은 null로 저장된다.
         */
        Image image = null;

        if (request.getImageId() != null) {
            // 여기서 요청으로 온 imageId를 가지고 이미지를 찾는다.
            image = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new CustomException(ErrorCode.IMAGE_NOT_FOUND));

            // 그리고 그 이미지가 POST타입이 맞는지 확인
            if (image.getImageType() != ImageType.POST) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            // 다른 게시글의 image_id를 참조하면 그 이미지를 뺏어서 방지
            if (image.getReferenceId() != null) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
        }

        Post post = Post.create(
                user,
                request.getTitle(),
                request.getContent()

        );
        /**
         * JpaRepository의 save()는 저장된 Post 엔티티를 반환한다.
         * JDBC 때처럼 Repository가 PostCreateResponse를 주지 않음
         */
        Post savedPost = postRepository.save(post);

        // 여기서 referenceId를 postId와 연결
        if (image != null) {
            image.connectReference(savedPost.getId());
        }

        // 이제 post 엔티티에 imageUrl을 가져올 수 없기 때문에
        // 직접 image에서 꺼내옴
        String responseImageUrl = image != null ? image.getImageUrl() : null;

        //작성자의 프로필 이미지를 직접 가져와야함
        //이걸 QueryDSL이나 @Query로 하는게 나을까
        Image writerProfileImage = imageRepository
                .findByImageTypeAndReferenceId(ImageType.USER, savedPost.getUser().getId())
                .orElse(null);

        String writerProfileImageUrl = writerProfileImage != null
                ? writerProfileImage.getImageUrl()
                : null;

        /**
         * 응답 DTO는 Service에서 저장된 엔티티 값을 이용해 직접 생성
         */
        return new PostCreateResponse(
                savedPost.getId(),
                savedPost.getTitle(),
                savedPost.getContent(),
                responseImageUrl,
                savedPost.getCreatedAt(),
                new PostWriterResponse(
                        savedPost.getUser().getId(),
                        savedPost.getUser().getNickname(),
                        writerProfileImageUrl
                )
        );

    }

    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long loginUserId) {

        /**
         * 게시글 상세 조회 전에 게시글이 존재하는지 확인
         */
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        /**
         * 게시글 상세 조회 시 조회수를 1 증가시킨다.
         */
        post.increaseViewCount();

        /**
         * 상세 조회 응답은 QueryDSL Custom Repository에서 DTO로 조회한다.
         *
         * loginUserId를 함께 넘기는 이유는
         * 현재 로그인 사용자가 이 게시글에 좋아요를 눌렀는지 여부를
         * 응답에 포함하기 위해서다.
         */
        return postRepository.findPostDetailById(postId, loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    // 게시글 수정할 때 새 이미지가 들어오면 기존 이미지 연결을 끊고 새 이미지를 연결함
    @Transactional
    public PostUpdateResponse updatePost(
            Long postId,
            Long loginUserId,
            PostUpdateRequest request
    ) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getId().equals(loginUserId)) {
            throw new CustomException(ErrorCode.POST_FORBIDDEN);
        }

        // 현재 게시글에 연결되어 있는 기존 이미지
        Image currentImage = imageRepository
                .findByImageTypeAndReferenceId(ImageType.POST, post.getId())
                .orElse(null);

        // 응답에 내려줄 이미지, DB 조회 중복을 줄이기 위해 복사해둠
        Image responseImage = currentImage;


        // 새 이미지가있다면 게시글과 연결된 이미지를 찾고 연결을 끊음
        if (request.getImageId() != null) {
            // 요청으로 온 imageId로 새 이미지 조회
            Image newImage = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new CustomException(ErrorCode.IMAGE_NOT_FOUND));

            // 게시글 이미지 타입인지 확인
            if (newImage.getImageType() != ImageType.POST) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            // 이미 다른 게시글에 연결되어있다면, 그 연결된 post_id가 로그인한 사용자와 다르다면 예외처리
            if (newImage.getReferenceId() != null
                    && !newImage.getReferenceId().equals(post.getId())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            if (currentImage != null) {
                currentImage.disconnectReference();
            }

            // 새 이미지를 현재 게시글과 연결
            newImage.connectReference(post.getId());
            // 기존 연결이미지를 찾아둔걸 newImage로 바꿈
            responseImage = newImage;

        }

        post.updatePost(
                request.getTitle(),
                request.getContent()
        );

        /**
         * 처음에는 수정 후에 영속상태의 post값을 가져오려고했으나
         * updated_at은 SQL이 실행되는 flush 시점에 갱신되기 땜ㄴ에
         * flush를 호출하여 변경 내용을 DB에 반영하여 가져옴
         */
        postRepository.flush();

        // 기존의 이미지나 새로운 이미지가 저장된 객체의 Url을 저장
        String responseImageUrl = responseImage != null ? responseImage.getImageUrl() : null;

        return new PostUpdateResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                responseImageUrl,
                post.getUpdatedAt()
        );
    }

    @Transactional
    public void deletePost(Long postId, Long loginUserId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getId().equals(loginUserId)) {
            throw new CustomException(ErrorCode.POST_DELETE_FORBIDDEN);
        }

        //이미지와는 FK로 연결되어있는 것이 아니기 때문에 disconnect를 시켜줘야함
        //그래야 나중에 referenceid가 null것을 모아 한번에 삭제할 수 있음
        imageRepository.findByImageTypeAndReferenceId(ImageType.POST, post.getId())
                .ifPresent(Image::disconnectReference);

        postRepository.delete(post);
    }


}