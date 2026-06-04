package org.example.community.postlike.service;

import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.post.entity.Post;
import org.example.community.post.repository.PostRepository;
import org.example.community.postlike.dto.response.PostLikeResponse;
import org.example.community.postlike.entity.PostLike;
import org.example.community.postlike.entity.PostLikeId;
import org.example.community.postlike.repository.PostLikeRepository;
import org.example.community.user.entity.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 게시글 존재 여부를 확인, 사용자가 이미 좋아요를 눌렀는지 확인
     * 좋아요를 안눌렀으면 post_like에 insert후 post에 like_count +1
     * 현재 like_count 조회후 응답
     */
    @Transactional
    public PostLikeResponse likePost(Long postId, Long loginUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(loginUserId, postId);

        boolean alreadyLiked = postLikeRepository.existsById(postLikeId);

        /**
         * 좋아요를 누르지 않았을 때 해야됨
         */
        if (alreadyLiked) {
            throw new CustomException(ErrorCode.ALREADY_LIKED_POST);
        }

        PostLike postLike = PostLike.create(user, post);

        postLikeRepository.save(postLike);

        /*
         * post는 현재 트랜잭션 안에서 조회된 영속 상태 엔티티이므로
         * increaseLikeCount()로 값만 변경해도
         * 트랜잭션 종료 시 JPA 변경 감지로 like_count UPDATE SQL이 실행
         */
        post.increaseLikeCount();

        return new PostLikeResponse(
                post.getId(),
                true,
                post.getLikeCount()
        );
    }

    @Transactional
    public PostLikeResponse unlikePost(Long postId, Long loginUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(loginUserId, postId);

        PostLike postLike = postLikeRepository.findById(postLikeId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_LIKE_NOT_FOUND));

        postLikeRepository.delete(postLike);

        /*
         * post는 현재 트랜잭션 안에서 조회된 영속 상태 엔티티이므로
         * decreaseLikeCount()로 값만 변경하면
         * 트랜잭션 종료 시 JPA 변경 감지로 like_count UPDATE SQL이 실행된다.
         */
        post.decreaseLikeCount();

        return new PostLikeResponse(
                post.getId(),
                false,
                post.getLikeCount()
        );
    }
}