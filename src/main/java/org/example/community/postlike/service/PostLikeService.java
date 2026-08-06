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

        if (alreadyLiked) {
            throw new CustomException(ErrorCode.ALREADY_LIKED_POST);
        }

        PostLike postLike = PostLike.create(user, post);

        /*
         * post_like INSERT보다 posts UPDATE를 먼저 실행해
         * posts 행의 X Lock을 먼저 획득한다.
         */
        int updatedCount = postRepository.increaseLikeCount(postId);

        if (updatedCount != 1) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }

        postLikeRepository.save(postLike);

        Long likeCount = postRepository.findLikeCountById(postId);

        return new PostLikeResponse(
                postId,
                true,
                likeCount
        );
    }

    @Transactional
    public PostLikeResponse unlikePost(Long postId, Long loginUserId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(loginUserId, postId);

        PostLike postLike = postLikeRepository.findById(postLikeId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_LIKE_NOT_FOUND));

        /*
         * post_like DELETE보다 posts UPDATE를 먼저 실행해서
         * posts 행의 X Lock을 먼저 획득한다.
         */
        int updatedCount = postRepository.decreaseLikeCount(postId);

        if (updatedCount != 1) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }

        postLikeRepository.delete(postLike);

        // 최신 좋아요 수 조회
        Long likeCount = postRepository.findLikeCountById(postId);

        return new PostLikeResponse(
                postId,
                false,
                likeCount
        );
    }
}