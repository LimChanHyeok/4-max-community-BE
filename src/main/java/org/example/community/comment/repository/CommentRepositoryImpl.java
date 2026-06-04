package org.example.community.comment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.community.comment.dto.response.CommentCreateResponse;
import org.example.community.comment.dto.response.CommentSummaryResponse;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.example.community.comment.entity.QComment.comment;
import static org.example.community.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    // QueryDSL로 쿼리를 만들기 위한 객체
    private final JPAQueryFactory queryFactory;

    // 댓글을 저장한 뒤 클라이언트에게 내려줄 응답 DTOf를 조회하는 메소드
    @Override
    public Optional<CommentCreateResponse> findCreateResponseById(Long commentId, Long loginUserId) {
        CommentCreateResponse response = queryFactory
                .select(Projections.constructor(
                        CommentCreateResponse.class,
                        comment.id,
                        comment.content,
                        comment.createdAt,
                        user.id,
                        user.nickname,
                        user.profileImage,
                        // 이 부분은 is_writer부분으로 댓글의 유저 아이디와 로그인한 아이디가 같은지 비교하는 것
                        comment.user.id.eq(loginUserId)
                ))
                .from(comment)
                .join(comment.user, user)
                .where(comment.id.eq(commentId))
                .fetchOne();

        return Optional.ofNullable(response);
    }

    // 특정 게시글의 댓글 목록을 커서 기반 페이징으로 조회하는 메소드
    @Override
    public List<CommentSummaryResponse> findCommentsByCursor(
            Long postId,
            Long cursor,
            Long loginUserId,
            int limit
    ) {
        return queryFactory
                .select(Projections.constructor(
                        CommentSummaryResponse.class,
                        comment.id,
                        comment.content,
                        comment.createdAt,
                        user.id,
                        user.nickname,
                        user.profileImage,
                        //이 부분도 is_writer부분 댓글의 userid와 로그인한 아이디를 비교하여 작성자인지 아닌지 판단
                        comment.user.id.eq(loginUserId)
                ))
                .from(comment)
                .join(comment.user, user)
                .where(
                        comment.post.id.eq(postId),
                        // cusor가 없다면 null을 반환하는데 QueryDSL의 where()는 null이 있으면 무시함
                        cursor == null ? null : comment.id.lt(cursor)
                )
                .orderBy(comment.id.desc())
                .limit(limit)
                .fetch();
    }

}