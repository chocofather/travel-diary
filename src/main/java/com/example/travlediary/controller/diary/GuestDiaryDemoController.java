package com.example.travlediary.controller.diary;

import com.example.travlediary.model.DiaryCoverMaterial;
import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.model.DiaryNotebookType;
import com.example.travlediary.model.DiaryNoteStyle;
import com.example.travlediary.service.diary.DiaryLabelFontCatalog;
import com.example.travlediary.service.diary.DiaryNoteCatalog;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 비회원 다이어리 체험.
 *
 * <p>이 컨트롤러는 화면과 꾸미기 목록만 내려준다. 체험 다이어리(draft)는 브라우저
 * localStorage 에만 남고 서버에는 아무것도 저장되지 않는다. 그래서 여기에는 GET 만 있고
 * 저장용 POST/PUT/DELETE 를 두지 않는다.
 *
 * <p>주입받는 것은 스티커/라벨·메모지/라벨기 글꼴 <b>목록</b>뿐이다. 셋 다 resources 의
 * manifest(json)를 읽는 카탈로그라 DB 를 타지 않는다. diaries/diary_pages/diary_elements 를
 * 다루는 Service 나 Mapper 는 하나도 주입받지 않으므로, 이 경로로는 DB 에 닿을 수 없다.
 *
 * <p>회원용 목록/편집은 {@link DiaryController} 가 그대로 맡는다. 이 화면 때문에
 * 회원용 저장 endpoint 의 인증이 느슨해지지 않는다. SecurityConfig 에서도
 * {@code GET /diaries/demo}, {@code /new}, {@code /edit}, {@code /cover} 네 건만 공개한다.
 */
@Controller
@RequiredArgsConstructor
public class GuestDiaryDemoController {

    /** 스티커 목록. 저장이 아니라 화면에 그릴 목록을 얻는 용도다. */
    private final DiaryStickerCatalog diaryStickerCatalog;
    /** 라벨 / 떡메모지 목록과 색 팔레트. */
    private final DiaryNoteCatalog diaryNoteCatalog;
    /** 라벨기 글꼴 목록. */
    private final DiaryLabelFontCatalog diaryLabelFontCatalog;

    /**
     * 비회원용 "나의 여행일기" 책장. 회원 {@code /diaries} 와 같은 자리다.
     *
     * <p>만들어 둔 체험 다이어리가 있는지는 서버가 알 수 없으므로, 화면에 들어온 뒤
     * 브라우저가 localStorage 를 보고 카드를 그리거나 빈 상태를 보여 준다.
     */
    @GetMapping("/diaries/demo")
    public String guestDiaryDemo(Model model) {
        /*
          카드에 그릴 표지에 마스킹테이프가 있을 수 있다. 저장된 그림 경로만으로는
          되풀이 조각을 알 수 없으므로 편집 화면과 같은 표를 함께 내려 준다.
          (manifest 를 읽는 카탈로그라 DB 를 타지 않는다)
        */
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        return "diary/demo";
    }

    /**
     * 비회원 체험 다이어리 만들기. 회원의 "새 여행일기" 와 같은 자리다.
     *
     * <p>제목·기간·노트 종류를 정하고 표지를 고르는 화면만 내려준다.
     * 실제 만들기는 브라우저가 draft 에 쓰는 것으로 끝난다.
     */
    @GetMapping("/diaries/demo/new")
    public String guestDiaryDemoNew(Model model) {
        // 제공 표지 8종과 노트 종류. 둘 다 enum 이라 DB 를 타지 않는다.
        model.addAttribute("coverStyles", DiaryCoverStyle.values());
        model.addAttribute("notebookTypes", DiaryNotebookType.values());
        model.addAttribute("pageTitle", "새 여행일기 체험 | Travel Diary");
        return "diary/demo-new";
    }

    /**
     * 비회원 체험 편집 화면.
     *
     * <p>서버는 빈 편집기와 꾸미기 목록만 내려준다. 어떤 장을 어떻게 그릴지는
     * 브라우저가 draft 를 읽어 정한다. draft 가 없으면 화면이 스스로 시작 화면으로 돌려보낸다.
     */
    @GetMapping("/diaries/demo/edit")
    public String guestDiaryDemoEditor(Model model) {
        // 회원 편집 화면과 같은 목록을 같은 이름으로 내려 준다. 화면 마크업이 같은 값을 쓴다.
        model.addAttribute("diaryStickerCategories", diaryStickerCatalog.getCategories());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("diaryLabelStyles",
                diaryNoteCatalog.getStyles(DiaryNoteStyle.CATEGORY_LABEL));
        model.addAttribute("diaryMemoStyles",
                diaryNoteCatalog.getStyles(DiaryNoteStyle.CATEGORY_MEMO));
        model.addAttribute("diaryNoteColors", diaryNoteCatalog.getColors());
        model.addAttribute("diaryLabelFonts", diaryLabelFontCatalog.getFonts());
        // 사진을 붙일 자리(일반 / 폴라로이드). 회원 화면과 같은 목록이다.
        model.addAttribute("coverPhotoStyles", DiaryCoverPhotoStyle.values());
        model.addAttribute("pageTitle", "여행일기 체험 | Travel Diary");
        return "diary/demo-edit";
    }

    /**
     * 비회원 체험 표지 편집 화면.
     *
     * <p>회원의 "내 표지 디자인" 보관함과 달리 체험 표지는 언제나 한 장뿐이라
     * 목록·이름·삭제 관리가 없다. 저장은 브라우저 draft 의 coverDesign 한 자리에서 끝난다.
     * 여기서도 서버는 빈 편집기와 재질/스티커/글꼴 목록만 내려준다.
     *
     * <p>표지는 다이어리 한 권의 속성이라 들어오는 자리도 나가는 자리도 책장 하나뿐이다.
     * (새로 만드는 중이든 책장 카드의 "표지 수정" 이든 끝나면 책장으로 돌아간다)
     */
    @GetMapping("/diaries/demo/cover")
    public String guestDiaryDemoCover(Model model) {
        // 표지 재질은 enum 이고, 나머지 목록은 manifest 를 읽는 카탈로그다. 둘 다 DB 가 아니다.
        model.addAttribute("coverMaterials", DiaryCoverMaterial.values());
        model.addAttribute("diaryStickerCategories", diaryStickerCatalog.getCategories());
        model.addAttribute("stickerRepeats", diaryStickerCatalog.getRepeatsByImageUrl());
        model.addAttribute("diaryLabelFonts", diaryLabelFontCatalog.getFonts());
        model.addAttribute("coverPhotoStyles", DiaryCoverPhotoStyle.values());
        model.addAttribute("pageTitle", "표지 꾸미기 체험 | Travel Diary");
        return "diary/demo-cover";
    }
}
