package com.ticketmind.controller;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.Result;
import com.ticketmind.common.ResultCode;
import com.ticketmind.model.dto.KnowledgeUploadResult;
import com.ticketmind.model.entity.KnowledgeChunk;
import com.ticketmind.service.impl.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeUploadResult> uploadKnowledgeFile(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "缺少上传文件");
        }

        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER_FORMAT, "文件名不能为空");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "读取上传文件失败");
        }

        List<KnowledgeChunk> chunks = knowledgeService.uploadDocument(filename, content);
        String source = chunks.isEmpty() ? filename : chunks.get(0).getSource();
        return Result.success(new KnowledgeUploadResult(filename, source, chunks.size()));
    }
}
