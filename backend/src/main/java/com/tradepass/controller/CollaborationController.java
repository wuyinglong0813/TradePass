package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.dto.response.FileChunkDataPayload;
import com.tradepass.dto.response.FileDataPayload;
import com.tradepass.service.ContractAttachmentService;
import com.tradepass.service.PersonalMemoService;
import com.tradepass.service.ReconciliationAccountService;
import com.tradepass.service.ReconciliationStatementService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@RestController
@RequestMapping("/api")
public class CollaborationController {
    private final ContractAttachmentService attachmentService;
    private final PersonalMemoService memoService;
    private final ReconciliationStatementService statementService;
    private final ReconciliationAccountService accountService;

    public CollaborationController(ContractAttachmentService attachmentService,
                                   PersonalMemoService memoService,
                                   ReconciliationStatementService statementService,
                                   ReconciliationAccountService accountService) {
        this.attachmentService = attachmentService;
        this.memoService = memoService;
        this.statementService = statementService;
        this.accountService = accountService;
    }

    @GetMapping("/contracts/{contractId}/attachments")
    public ApiResponse<List<Map<String, Object>>> listAttachments(@PathVariable Long contractId,
                                                                  @RequestParam String category) {
        return ApiResponse.ok(attachmentService.list(contractId, category));
    }

    @PostMapping(value = "/contracts/{contractId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadAttachment(
            @PathVariable Long contractId,
            @RequestParam String category,
            @RequestParam(required = false) String originalName,
            @RequestParam(required = false) String voucherDate,
            @RequestParam(required = false) String voucherAmount,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String invoiceDate,
            @RequestParam(required = false) String invoiceAmount,
            @RequestParam MultipartFile file) {
        try {
            String name = originalName == null || originalName.isBlank()
                    ? file.getOriginalFilename() : originalName;
            return ApiResponse.ok(attachmentService.upload(contractId, category, name,
                    file.getBytes(), voucherDate, voucherAmount,
                    invoiceNo, invoiceDate, invoiceAmount));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("文件读取失败，请重新选择");
        }
    }

    @PostMapping(value = "/contracts/{contractId}/attachments/base64",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> uploadAttachmentBase64(
            @PathVariable Long contractId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(attachmentService.upload(
                contractId,
                string(body, "category"),
                string(body, "originalName"),
                decodeBase64(body.get("contentBase64")),
                string(body, "voucherDate"),
                string(body, "voucherAmount"),
                string(body, "invoiceNo"),
                string(body, "invoiceDate"),
                string(body, "invoiceAmount")));
    }

    @PostMapping(value = "/contract-attachments/{id}/decision",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> decideAttachment(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(attachmentService.decide(id,
                String.valueOf(body.getOrDefault("decision", "")),
                String.valueOf(body.getOrDefault("reason", ""))));
    }

    @PostMapping(value = "/contract-attachments/{id}/decision",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> decideAttachmentWithSignature(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam(required = false) String reason,
            @RequestParam("signature") MultipartFile signature) {
        try {
            return ApiResponse.ok(attachmentService.decide(id, decision, reason,
                    signature.getOriginalFilename(), signature.getBytes()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("签名图片读取失败，请重新签名");
        }
    }

    @PostMapping("/contract-attachments/{id}/withdraw")
    public ApiResponse<String> withdrawAttachment(@PathVariable Long id) {
        return ApiResponse.ok(attachmentService.withdraw(id));
    }

    @PostMapping("/contract-attachments/{id}/delete")
    public ApiResponse<String> deleteAttachment(@PathVariable Long id) {
        return ApiResponse.ok(attachmentService.delete(id));
    }

    @GetMapping("/contract-attachments/{id}/content")
    public ResponseEntity<byte[]> attachmentContent(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "false") boolean download) {
        ContractAttachmentService.FilePayload file = attachmentService.getFile(id);
        return fileResponse(file.originalName(), file.contentType(), file.data(), download);
    }

    @GetMapping("/contract-attachments/{id}/content-data")
    public ApiResponse<FileDataPayload> attachmentContentData(@PathVariable Long id) {
        ContractAttachmentService.FilePayload file = attachmentService.getFile(id);
        return ApiResponse.ok(FileDataPayload.of(file.originalName(), file.contentType(), file.data()));
    }

    @GetMapping("/contract-attachments/{id}/content-chunk-data")
    public ApiResponse<FileChunkDataPayload> attachmentContentChunkData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "655360") int size) {
        if (offset < 0 || size <= 0 || size > 640 * 1024) {
            throw new BusinessException("文件分片参数不正确");
        }
        ContractAttachmentService.FilePayload file = attachmentService.getFile(id);
        int totalSize = file.data().length;
        if (offset >= totalSize) {
            throw new BusinessException("文件分片位置超出范围");
        }
        int start = (int) offset;
        int end = Math.min(totalSize, start + size);
        byte[] chunk = Arrays.copyOfRange(file.data(), start, end);
        return ApiResponse.ok(FileChunkDataPayload.of(
                file.originalName(), file.contentType(), chunk,
                offset, totalSize, end == totalSize));
    }

    @GetMapping("/contracts/{id}/memo")
    public ApiResponse<Map<String, Object>> contractMemo(@PathVariable Long id) {
        return ApiResponse.ok(memoService.get(PersonalMemoService.CONTRACT, id));
    }

    @PostMapping("/contracts/{id}/memo")
    public ApiResponse<Map<String, Object>> saveContractMemo(@PathVariable Long id,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(memoService.save(PersonalMemoService.CONTRACT, id,
                String.valueOf(body.getOrDefault("content", ""))));
    }

    @GetMapping("/trade-documents/{id}/memo")
    public ApiResponse<Map<String, Object>> salesOrderMemo(@PathVariable Long id) {
        return ApiResponse.ok(memoService.get(PersonalMemoService.SALES_ORDER, id));
    }

    @PostMapping("/trade-documents/{id}/memo")
    public ApiResponse<Map<String, Object>> saveSalesOrderMemo(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(memoService.save(PersonalMemoService.SALES_ORDER, id,
                String.valueOf(body.getOrDefault("content", ""))));
    }

    @GetMapping("/reconciliation-statements")
    public ApiResponse<List<Map<String, Object>>> statements(
            @RequestParam(required = false) Long counterpartyCompanyId) {
        return ApiResponse.ok(statementService.list(counterpartyCompanyId));
    }

    @GetMapping("/reconciliation-accounts")
    public ApiResponse<List<Map<String, Object>>> reconciliationAccounts() {
        return ApiResponse.ok(accountService.listAccounts());
    }

    @GetMapping("/reconciliation-accounts/{counterpartyCompanyId}")
    public ApiResponse<Map<String, Object>> reconciliationAccount(
            @PathVariable Long counterpartyCompanyId) {
        return ApiResponse.ok(accountService.account(counterpartyCompanyId));
    }

    @GetMapping("/reconciliation-accounts/{counterpartyCompanyId}/workbook")
    public ResponseEntity<byte[]> reconciliationWorkbook(
            @PathVariable Long counterpartyCompanyId,
            @RequestParam(defaultValue = "false") boolean download) {
        ReconciliationAccountService.WorkbookPayload workbook =
                accountService.workbook(counterpartyCompanyId);
        return fileResponse(workbook.originalName(), workbook.contentType(), workbook.data(), download);
    }

    @GetMapping("/reconciliation-accounts/{counterpartyCompanyId}/workbook-data")
    public ApiResponse<FileDataPayload> reconciliationWorkbookData(
            @PathVariable Long counterpartyCompanyId) {
        ReconciliationAccountService.WorkbookPayload workbook =
                accountService.workbook(counterpartyCompanyId);
        return ApiResponse.ok(FileDataPayload.of(
                workbook.originalName(), workbook.contentType(), workbook.data()));
    }

    @PostMapping(value = "/reconciliation-statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadStatement(
            @RequestParam Long counterpartyCompanyId,
            @RequestParam String period,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String originalName,
            @RequestParam MultipartFile file) {
        try {
            String name = originalName == null || originalName.isBlank()
                    ? file.getOriginalFilename() : originalName;
            return ApiResponse.ok(statementService.upload(counterpartyCompanyId, period, remark,
                    name, file.getBytes()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Excel 文件读取失败，请重新选择");
        }
    }

    @GetMapping("/reconciliation-statements/{id}/content")
    public ResponseEntity<byte[]> statementContent(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "false") boolean download) {
        ReconciliationStatementService.FilePayload file = statementService.getFile(id);
        return fileResponse(file.originalName(), file.contentType(), file.data(), download);
    }

    @GetMapping("/reconciliation-statements/{id}/content-data")
    public ApiResponse<FileDataPayload> statementContentData(@PathVariable Long id) {
        ReconciliationStatementService.FilePayload file = statementService.getFile(id);
        return ApiResponse.ok(FileDataPayload.of(file.originalName(), file.contentType(), file.data()));
    }

    private byte[] decodeBase64(Object value) {
        try {
            return Base64.getDecoder().decode(value == null ? "" : String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("文件读取失败，请重新选择");
        }
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private ResponseEntity<byte[]> fileResponse(String fileName, String contentType,
                                                byte[] data, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(data);
    }
}
