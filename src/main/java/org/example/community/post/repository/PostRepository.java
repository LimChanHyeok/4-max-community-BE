package org.example.community.post.repository;

import org.example.community.post.dto.response.PostSummaryResponse;
import org.example.community.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>,PostRepositoryCustom {


}