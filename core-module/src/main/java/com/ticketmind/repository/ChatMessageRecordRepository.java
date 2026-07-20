package com.ticketmind.repository;

import com.ticketmind.model.entity.ChatMessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRecordRepository extends JpaRepository<ChatMessageRecord, Long> {

    List<ChatMessageRecord> findBySession_PublicIdAndSession_User_IdOrderByCreatedAtAscIdAsc(String publicId, Long userId);
}
