package com.ticketmind.repository;

import com.ticketmind.model.entity.ChatMessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRecordRepository extends JpaRepository<ChatMessageRecord, Long> {

    List<ChatMessageRecord> findBySession_IdAndSession_User_IdOrderByCreatedAtAscIdAsc(Long sessionId, Long userId);

    long deleteBySession_Id(Long sessionId);
}
