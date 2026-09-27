package com.example.travlediary.controller.admin;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.wikidata.WikidataApiException;
import com.example.travlediary.service.wikidata.WikidataBulkRegistrationService;
import com.example.travlediary.service.wikidata.WikipediaApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * 해외 여행지(Wikidata) 일괄 등록 API. 검색·검토(GET)는 저장하지 않고, 등록(POST)은 여행지 한 곳씩 받는다.
 * 여러 곳을 등록할 때는 화면이 여행지마다 요청을 보내 진행 상태를 표시한다.
 */
@RestController
@RequestMapping("/admin/api/wikidata/bulk")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWikidataBulkImportController {

    private final WikidataBulkRegistrationService bulkRegistrationService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "0") int offset) {
        return respond(() -> bulkRegistrationService.search(keyword, offset));
    }

    /** 기존 지역을 Wikidata 행정구역으로 매핑한 결과와, 더 좁혀 볼 수 있는 하위 시 목록. */
    @GetMapping("/regions/resolve")
    public ResponseEntity<?> resolveRegion(@RequestParam(required = false) Long regionId) {
        return respond(() -> bulkRegistrationService.resolveRegion(regionId));
    }

    /** 지역(또는 그 안의 시)의 여행 관련 장소 한 페이지. */
    @GetMapping("/regions/candidates")
    public ResponseEntity<?> regionCandidates(@RequestParam(required = false) Long regionId,
                                              @RequestParam(required = false) String cityQid,
                                              @RequestParam(defaultValue = "0") int offset) {
        return respond(() -> bulkRegistrationService.regionSearch(regionId, cityQid, offset));
    }

    @GetMapping("/review")
    public ResponseEntity<?> review(@RequestParam(required = false) String qid) {
        return respond(() -> bulkRegistrationService.review(qid));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody WikidataBulkRegistrationService.RegisterRequest request,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인 정보를 확인할 수 없습니다.");
        }
        return ResponseEntity.ok(bulkRegistrationService.register(request, userDetails.getId()));
    }

    private ResponseEntity<?> respond(Supplier<?> body) {
        try {
            return ResponseEntity.ok(body.get());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (WikidataApiException | WikipediaApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    private record ErrorResponse(String message) {
    }
}
