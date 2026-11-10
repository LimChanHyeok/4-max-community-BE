package org.example.community.post.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.community.image.entity.ImageType;
import org.example.community.image.entity.QImage;
import org.example.community.post.dto.response.PostCreateResponse;
import org.example.community.post.dto.response.PostDetailResponse;
import org.example.community.post.dto.response.PostSummaryResponse;
import org.example.community.post.dto.response.PostWriterResponse;
import org.example.community.post.entity.QPost;
import org.example.community.user.entity.QUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;

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
    private final NamedParameterJdbcTemplate jdbcTemplate;
    // 같은 이미지 테이블 이지만 두번 leftJoin하기 위해 별칭을 사용함
    private static final QImage postImage = new QImage("postImage");
    private static final QImage writerProfileImage = new QImage("writerProfileImage");


    // 커서를 이용한 게시글 목록 조회를 위한 메소드
    @Override
    public List<PostSummaryResponse> findPostsByCursor(Long cursor, int limit, List<String> searchTerms) {
        if (!searchTerms.isEmpty()) {
            return findSearchPostsByCursor(cursor, limit, searchTerms);
        }
        BooleanBuilder conditions = new BooleanBuilder();
        if (cursor != null) {
            conditions.and(post.id.lt(cursor));
        }
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
                                writerProfileImage.imageUrl
                        )
                ))
                .from(post)
                .join(post.user, user)
                .leftJoin(writerProfileImage)
                .on(
                        writerProfileImage.imageType.eq(ImageType.USER),
                        writerProfileImage.referenceId.eq(user.id)
                )
                .where(conditions)
                .orderBy(post.id.desc())
                .limit(limit)
                .fetch();
    }

    private List<PostSummaryResponse> findSearchPostsByCursor(Long cursor, int limit, List<String> searchTerms) {
        // 최신순 검색에서 PK를 역순으로 읽도록 지정한다.
        StringBuilder sql = new StringBuilder("""
                SELECT p.id AS post_id, p.title, p.created_at,
                       p.like_count, p.comment_count, p.view_count,
                       u.id AS writer_id, u.nickname, i.image_url
                FROM posts AS p FORCE INDEX (PRIMARY)
                JOIN users AS u ON u.id = p.user_id
                LEFT JOIN images AS i ON i.image_type = 'USER' AND i.reference_id = u.id
                WHERE 1 = 1
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource("limit", limit);
        if (cursor != null) {
            sql.append(" AND p.id < :cursor");
            parameters.addValue("cursor", cursor);
        }
        for (int index = 0; index < searchTerms.size(); index++) {
            String parameter = "term" + index;
            // QueryDSL contains와 동일하게 !, %, _를 검색어의 문자로 취급한다.
            String term = searchTerms.get(index)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            sql.append(" AND (p.title LIKE :").append(parameter)
                    .append(" ESCAPE '!' OR p.content LIKE :").append(parameter)
                    .append(" ESCAPE '!')");
            parameters.addValue(parameter, "%" + term + "%");
        }
        sql.append(" ORDER BY p.id DESC LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), parameters, (result, rowNumber) ->
                new PostSummaryResponse(
                        result.getLong("post_id"),
                        result.getString("title"),
                        result.getObject("created_at", LocalDateTime.class),
                        result.getLong("like_count"),
                        result.getLong("comment_count"),
                        result.getLong("view_count"),
                        new PostWriterResponse(
                                result.getLong("writer_id"),
                                result.getString("nickname"),
                                result.getString("image_url")
                        )
                ));
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
                        postImage.imageUrl,
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
                                writerProfileImage.imageUrl
                        )
                ))
                .from(post)
                // 작성자 정보를 가져오기 위해 join
                .join(post.user, user)
                .leftJoin(postImage)
                .on(
                        postImage.imageType.eq(ImageType.POST),
                        postImage.referenceId.eq(post.id)
                )
                .leftJoin(writerProfileImage)
                .on(
                        writerProfileImage.imageType.eq(ImageType.USER),
                        writerProfileImage.referenceId.eq(user.id)
                )
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

    @Override
    public Optional<PostCreateResponse> findCreateResponseById(Long postId) {

        QPost post = QPost.post;
        QUser user = QUser.user;

        QImage postImage = new QImage("postImage");
        QImage writerProfileImage = new QImage("writerProfileImage");

        PostCreateResponse response = queryFactory
                .select(Projections.constructor(
                        PostCreateResponse.class,
                        post.id,
                        post.title,
                        post.content,
                        postImage.imageUrl,
                        post.createdAt,
                        Projections.constructor(
                                PostWriterResponse.class,
                                user.id,
                                user.nickname,
                                writerProfileImage.imageUrl
                        )
                ))
                .from(post)
                .join(post.user, user)
                .leftJoin(postImage)
                .on(
                        postImage.imageType.eq(ImageType.POST),
                        postImage.referenceId.eq(post.id)
                )
                .leftJoin(writerProfileImage)
                .on(
                        writerProfileImage.imageType.eq(ImageType.USER),
                        writerProfileImage.referenceId.eq(user.id)
                )
                .where(post.id.eq(postId))
                .fetchOne();

        return Optional.ofNullable(response);
    }

}