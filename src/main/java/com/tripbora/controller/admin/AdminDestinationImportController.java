package com.tripbora.controller.admin;

import com.tripbora.dto.destinationimport.DestinationImportPreview;
import com.tripbora.dto.destinationimport.DestinationImportResult;
import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.destinationimport.DestinationImportMasterExportService;
import com.tripbora.service.destinationimport.DestinationImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행지 JSON 일괄등록 API. 미리보기는 저장하지 않고, 등록은 여행지 한 건씩 받는다.
 * 화면은 선택한 여행지마다 등록 요청을 차례로 보내 진행 상태를 표시한다.
 */
@RestController
@RequestMapping("/admin/api/destinations/import-json")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDestinationImportController {

    private final DestinationImportService importService;
    private final DestinationImportMasterExportService masterExportService;

    /** 본문은 JSON 파일 내용 그대로다. 문법 오류도 미리보기 결과(fileErrors)로 돌려준다. */
    @PostMapping("/preview")
    public DestinationImportPreview preview(@RequestBody(required = false) String json) {
        return importService.preview(json);
    }

    /** 본문: {"index": 0, "allowPossibleDuplicate": false, "item": {...destinations[0]...}} */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) String body,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("로그인 정보를 확인할 수 없습니다."));
        }
        DestinationImportResult result = importService.register(body, userDetails.getId());
        return ResponseEntity.ok(result);
    }

    /** AI 가 JSON 을 만들 때 참고할 현재 등록 기준 데이터. 읽기 전용이며 DB 번호는 담지 않는다. */
    @GetMapping("/master")
    public ResponseEntity<DestinationImportMasterExportService.MasterExport> master() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(DestinationImportMasterExportService.FILE_NAME).build().toString())
                .body(masterExportService.export());
    }

    private record ErrorResponse(String message) {
    }
}
