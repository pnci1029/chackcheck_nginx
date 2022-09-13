package com.example.checkcheck.repository;


import com.example.checkcheck.model.articleModel.Article;
import com.example.checkcheck.model.commentModel.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface CommentRepository extends JpaRepository<Comment, Long> , CommentRepositoryCustom{
    List<Comment> findByArticle_ArticleId(Long articlesId);

    List<Comment> findByMember_MemberIdAndArticleArticleId(Long memberId, Long articleId);
}
