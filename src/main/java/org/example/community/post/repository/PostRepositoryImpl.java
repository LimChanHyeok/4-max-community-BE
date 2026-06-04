package org.example.community.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.community.post.dto.response.PostDetailResponse;
import org.example.community.post.dto.response.PostSummaryResponse;
import org.example.community.post.dto.response.PostWriterResponse;

import java.util.List;
import java.util.Optional;

import static org.example.community.post.entity.QPost.post;
import static org.example.community.postlike.entity.QPostLike.postLike;
import static org.example.community.user.entity.QUser.user;

@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    /** QueryDSL에서 JPQL 쿼리를 자바 코드 형태로 작성하고 실행할 수 있도록 도와주는 핵심 클래스
     * queryFactory
     * 		.select(user)
     * 		.from(user)
     * 		.where(user.nickname.eq("tester"))
     * 		.fetch(); 이런식으로 사용
     */
    private final JPAQueryFactory queryFactory;


    // 커서를 이용한 게시글 목록 조회를 위한 메소드
    @Override
    public List<PostSummaryResponse> findPostsByCursor(Long cursor, int limit) {
        return queryFactory
                // Projections -> 엔티티 전체가 아닌 필요한 필드만 선택해 조회할 수 있도록 지원하는 데이터 조회 방식, 그 중 DTO 프로젝션 사용
                // constructor -> 생성자를 이용
                .select(Projections.constructor(
                        PostSummaryResponse.class,
                        post.id,
                        post.title,
                        post.createdAt,
                        post.likeCount,
                        post.commentCount,
                        post.viewCount,
                        Projections.constructor(
                                PostWriterResponse.class,
                                user.id,
                                user.nickname,
                                user.profileImage
                        )
                ))
                .from(post)
                .join(post.user, user)
                //where절에 조건이 하나라서 BooleanBuilder를 안씀, lt는 작다(<) 비교
                .where(cursor != null ? post.id.lt(cursor) : null)
                .orderBy(post.id.desc())
                .limit(limit)
                .fetch();
    }

    // 게시글 상세 조회를 위한 메소드
    @Override
    public Optional<PostDetailResponse> findPostDetailById(Long postId, Long loginUserId) {
        PostDetailResponse result = queryFactory
                .select(Projections.constructor(
                        PostDetailResponse.class,
                        post.id,
                        post.title,
                        post.content,
                        post.imageUrl,
                        post.createdAt,
                        post.likeCount,
                        post.commentCount,
                        post.viewCount,
                        postLike.id.isNotNull(),
                        post.user.id.eq(loginUserId),
                        Projections.constructor(
                                PostWriterResponse.class,
                                user.id,
                                user.nickname,
                                user.profileImage
                        )
                ))
                .from(post)
                // 작성자 정보를 가져오기 위해 join
                .join(post.user, user)
                // leftjoin -> 좋아요를 누른 사람도 안누른 사람도 있기 때문에 leftjoin을 안쓰면 좋아요가 없는 게시글은 조회 결과에 안나타남
                // 좋아요 행이 있으면 같이 가져오고 없으면 null로 둠
                // 이 left join으로 이 사용자가 이 게시글에 좋아요를 눌렀는지 안눌렀는지 확인함 이게 postLike.id.isNotNull()로 들어감
                .leftJoin(postLike)
                .on(
                        postLike.post.id.eq(post.id),
                        postLike.user.id.eq(loginUserId)
                )
                // eq = equals
                .where(post.id.eq(postId))
                .fetchOne();
        // null값이 나올 수 있기 때문에 ofNullable로 감쌈
        return Optional.ofNullable(result);
    }

}