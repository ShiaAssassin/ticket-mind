package com.ticketmind.repository;

import java.util.List;
import java.util.Optional;

import com.ticketmind.model.entity.ChatSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 会话的数据访问接口。
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** 学生继续对话时，必须校验会话属于当前用户。 */
    Optional<ChatSession> findByIdAndUser_Id(Long id, Long userId);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<ChatSession> findById(Long id);

    List<ChatSession> findByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);
}
