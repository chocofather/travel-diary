package com.example.travlediary.controller.diary;

import com.example.travlediary.dto.GuestDiaryImportManifest;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.diary.GuestDiaryImportService;
import com.example.travlediary.service.diary.GuestDiaryImportTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 체험 여행일기를 내 여행일기로 가져오는 자리.
 *
 * <p>인증을 마친 사람만 들어온다. {@code /diaries/**} 가 이미 로그인 사용자 전용이라
 * 이 경로도 그 규칙을 그대로 받는다. (체험 경로 {@code /diaries/demo...} 만 따로 열려 있다)
 *
 * <p>화면(GET)은 빈 틀만 내려준다. 무엇이 있는지는 브라우저가 자기 저장소를 읽어 판단한다.
 * 저장(POST)은 브라우저가 보낸 값을 서버가 처음부터 다시 검증한 뒤에만 이루어진다.
 * 누구의 여행일기가 되는지는 요청 값이 아니라 로그인 정보로만 정해진다.
 */
@Controller
@RequiredArgsConstructor
public class GuestDiaryImportController {

    /** 확인 화면이 끊어 준 표를 담아 오는 칸. */
    private static final String IMPORT_TOKEN_PARAMETER = "importToken";
    /** 체험 여행일기 내용을 담아 오는 칸. */
    private static final String MANIFEST_PARAMETER = "manifest";
    /** 사진이 올라오는 칸의 앞머리. 그 밖의 파일 칸은 받지 않는다. */
    private static final String PHOTO_PART_PREFIX = "photo";

    private static final Logger log = LoggerFactory.getLogger(GuestDiaryImportController.class);

    private final GuestDiaryImportService guestDiaryImportService;
    /** 스티커 목록. 미리보기의 마스킹테이프 조각 경로를 얻는 용도다. (DB 를 타지 않는다) */
    private final DiaryStickerCatalog diaryStickerCatalog;
    private final ObjectMapper objectMapper;

    /**
     * 가져오기 확인 화면.
     *
     * <p>로그인하지 않은 채 들어오면 Spring Security 가 평소처럼 로그인 화면으로 보내면서
     * 이 주소를 기억해 둔다. 인증이 끝나면 {@code CustomLoginSuccessHandler} 가
     * 그 기억을 따라 다시 이 화면으로 데려온다. 그래서 별도의 복귀 장치를 만들지 않았다.
     *
     * <p>여기에서 가져오기 표를 한 장 끊어 준다. 저장은 그 표를 들고 와야 받아 준다.
     */
    @GetMapping("/diaries/import")
    public String guestDiaryImport(HttpSession session, Model model) {
        model.addAttribute("importToken", GuestDiaryImportTokens.issue(session));
        /*
          미리보기 표지에 마스킹테이프가 있을 수 있다. 저장된 그림 경로만으로는 되풀이 조각을
          알 수 없으므로 편집 화면과 같은 표를 함께 내려 준다. (manifest 를 읽는 카탈로그다)
        */
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("pageTitle", "체험 여행일기 가져오기 | Travel Diary");
        return "diary/import";
    }

    /**
     * 실제로 옮겨 담기.
     *
     * <p>보내오는 값은 전부 신뢰하지 않는다. 소유자는 로그인 정보로만 정해지고,
     * 무엇을 저장할지는 {@link GuestDiaryImportService} 가 처음부터 다시 판단한다.
     *
     * <p>같은 표로 두 번 저장하지 않는다. 이미 마친 표가 다시 오면 새로 만들지 않고
     * 그때 만들어진 여행일기 번호를 그대로 돌려준다. (응답이 도중에 끊긴 경우를 위해서다)
     */
    @PostMapping("/diaries/import")
    @ResponseBody
    public ResponseEntity<?> importGuestDiary(
            @RequestParam(name = IMPORT_TOKEN_PARAMETER, required = false) String importToken,
            @RequestParam(name = MANIFEST_PARAMETER, required = false) String manifestJson,
            MultipartHttpServletRequest request,
            HttpSession session,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다."));
        }
        // 이 세션에서 끊어 준 표만 받는다. 남의 표나 지어낸 표로는 저장되지 않는다.
        if (!GuestDiaryImportTokens.isIssued(session, importToken)) {
            return badRequest("가져오기 요청을 다시 시작해 주세요.");
        }

        // 이미 마친 표다. 한 권을 더 만들지 않고 그때의 결과를 그대로 돌려준다.
        Long alreadyImported = GuestDiaryImportTokens.completedDiaryId(session, importToken);
        if (alreadyImported != null) {
            return ResponseEntity.ok(Map.of("success", true, "diaryId", alreadyImported));
        }

        GuestDiaryImportManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestJson, GuestDiaryImportManifest.class);
        } catch (Exception exception) {
            return badRequest("가져올 여행일기 정보를 읽지 못했습니다.");
        }

        Map<String, MultipartFile> photoParts;
        try {
            photoParts = photoParts(request);
        } catch (ResponseStatusException exception) {
            return badRequest(exception.getReason());
        }

        try {
            Long diaryId = guestDiaryImportService.importDraft(
                    userDetails.getId(), manifest, photoParts);
            // 저장이 끝난 뒤에만 표를 소진한다. 실패했으면 같은 표로 다시 시도할 수 있다.
            GuestDiaryImportTokens.complete(session, importToken, diaryId);
            return ResponseEntity.ok(Map.of("success", true, "diaryId", diaryId));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(exception.getStatusCode())
                        .body(Map.of("success", false, "message",
                                exception.getReason() == null
                                        ? "여행일기를 저장하지 못했습니다." : exception.getReason()));
            }
            throw exception;
        } catch (RuntimeException exception) {
            // 실패 사유는 남기되 내부 사정을 화면에 그대로 내보내지 않는다.
            log.error("Guest diary import failed: userId={}, exceptionType={}",
                    userDetails.getId(), exception.getClass().getSimpleName(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message",
                            "여행일기를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }

    /**
     * 함께 올라온 사진.
     *
     * <p>이름표는 우리가 정한 앞머리를 가진 칸만 받는다. 파일 이름이나 칸 이름을
     * 저장 경로로 쓰지 않으므로 여기에서 경로가 새어 나갈 자리는 없다.
     * 한 칸에 여러 파일이 담겨 오면 어느 쪽이 무엇인지 알 수 없어 받지 않는다.
     */
    private Map<String, MultipartFile> photoParts(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> parts = new HashMap<>();
        for (Map.Entry<String, List<MultipartFile>> entry
                : request.getMultiFileMap().entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(PHOTO_PART_PREFIX)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 정보가 맞지 않습니다.");
            }
            List<MultipartFile> files = entry.getValue();
            if (files == null || files.size() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 정보가 맞지 않습니다.");
            }
            parts.put(name, files.get(0));
        }
        return parts;
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", message == null ? "가져오기 요청을 확인해 주세요." : message));
    }
}
