package com.example.travlediary.controller.admin;

import com.example.travlediary.service.wikidata.WikidataApiException;
import com.example.travlediary.service.wikidata.WikidataDestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/** 관리자 화면의 해외 여행지 후보 조회 전용 API. 저장 요청은 제공하지 않는다. */
@RestController
@RequestMapping("/admin/api/wikidata/destinations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWikidataDestinationController {

    private final WikidataDestinationService destinationService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword) {
        try {
            return ResponseEntity.ok(destinationService.search(keyword));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (WikidataApiException exception) {
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam(required = false) String qid) {
        try {
            return ResponseEntity.ok(destinationService.preview(qid));
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

    private record ErrorResponse(String message) {
    }
}
