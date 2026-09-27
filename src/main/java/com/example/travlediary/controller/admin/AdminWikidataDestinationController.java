package com.example.travlediary.controller.admin;

import com.example.travlediary.service.wikidata.WikidataApiException;
import com.example.travlediary.service.wikidata.WikidataDestinationService;
import com.example.travlediary.service.wikidata.WikipediaDescriptionService;
import com.example.travlediary.service.wikidata.CommonsPhotoPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Locale;
import java.util.function.Supplier;

/** 관리자 화면의 해외 여행지 후보 조회 전용 API. 저장 요청은 제공하지 않는다. */
@RestController
@RequestMapping("/admin/api/wikidata/destinations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWikidataDestinationController {

    private final WikidataDestinationService destinationService;
    private final WikipediaDescriptionService wikipediaDescriptionService;
    private final CommonsPhotoPreviewService commonsPhotoPreviewService;

    /** 검색 결과 목록을 바로 보여주기 위한 최소 정보(QID·이름·설명). */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword) {
        try {
            return timed(() -> destinationService.quickSearch(keyword));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    /** 검색 결과의 국가·지역·이미지와 장소 여부. 목록 표시 뒤에 따로 불러온다. */
    @GetMapping("/search-details")
    public ResponseEntity<?> searchDetails(@RequestParam(required = false) List<String> qids) {
        try {
            return timed(() -> destinationService.searchDetails(qids));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam(required = false) String qid) {
        try {
            return timed(() -> destinationService.previewForAutofill(qid));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @GetMapping("/wikipedia")
    public ResponseEntity<?> wikipedia(@RequestParam(required = false) String qid) {
        try {
            return timed(() -> wikipediaDescriptionService.previewForAutofill(qid));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @GetMapping("/commons-photos")
    public ResponseEntity<?> commonsPhotos(@RequestParam(required = false) String qid,
                                           @RequestParam(required = false) String cursor) {
        try {
            // cursor 는 앞 묶음이 준 nextCursor. 없으면 첫 묶음을 준다.
            return timed(() -> commonsPhotoPreviewService.preview(qid, cursor));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (NoSuchElementException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    private ResponseEntity<?> timed(Supplier<?> response) {
        long start = System.nanoTime();
        Object body = response.get();
        double milliseconds = (System.nanoTime() - start) / 1_000_000.0;
        return ResponseEntity.ok().header("Server-Timing",
                String.format(Locale.ROOT, "app;dur=%.1f", milliseconds)).body(body);
    }

    private record ErrorResponse(String message) {
    }
}
