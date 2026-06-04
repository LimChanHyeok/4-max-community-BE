package org.example.community.postlike.repository;

import org.example.community.postlike.entity.PostLike;
import org.example.community.postlike.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

}