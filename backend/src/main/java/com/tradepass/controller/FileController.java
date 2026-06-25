package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.UploadTokenPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenPayload> uploadToken(@Valid @RequestBody UploadTokenRequest request) {
        String objectKey = "tradepass/" + request.bizType() + "/" + UUID.randomUUID() + "-" + request.fileName();
        return ApiResponse.ok(new UploadTokenPayload(
                objectKey,
                "https://storage.example.com/" + objectKey,
                "mvp-upload-token",
                Instant.now().plusSeconds(900).toString()
        ));
    }

    public record UploadTokenRequest(
            @NotBlank(message = "业务类型不能为空") String bizType,
            @NotBlank(message = "文件名不能为空") String fileName
    ) {
    }
}
