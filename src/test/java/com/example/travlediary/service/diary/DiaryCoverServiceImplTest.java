package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverElement;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 적용된 표지에는 소유자 칸이 없다. 그래서 소유권은 늘 다이어리 쪽에서 확인한다.
 * 표지 행이 없는 것은 오류가 아니라 "기본 표지를 쓰는 다이어리"라는 뜻이다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryCoverServiceImplTest {

    @Mock
    private DiaryService diaryService;
    @Mock
    private DiaryCoverDesignService diaryCoverDesignService;
    @Mock
    private DiaryCoverDesignElementService diaryCoverDesignElementService;
    @Mock
    private DiaryCoverMapper diaryCoverMapper;
    @Mock
    private DiaryCoverElementMapper diaryCoverElementMapper;
    @Mock
    private FileUploadService fileUploadService;

    private DiaryCoverService service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverServiceImpl(diaryService, diaryCoverDesignService,
                diaryCoverDesignElementService, diaryCoverMapper, diaryCoverElementMapper,
                fileUploadService);
    }

    @Test
    void aDiaryThatIsNotMineIsBlockedBeforeTheCoverIsEvenRead() {
        when(diaryService.getMyDiary(10L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.findMyCover(10L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("다이어리를 찾을 수 없습니다.");
        verify(diaryCoverMapper, never()).findByDiaryIdAndUserId(any(), any());
    }

    /** 커스텀 표지가 없는 것은 정상이다. 그런 다이어리는 예전 그대로 기본 표지를 쓴다. */
    @Test
    void havingNoCustomCoverIsNormalAndComesBackEmpty() {
        when(diaryCoverMapper.findByDiaryIdAndUserId(10L, 7L)).thenReturn(null);

        assertThat(service.findMyCover(10L, 7L)).isEmpty();
        // 반드시 있어야 하는 자리에서만 오류가 된다
        assertThatThrownBy(() -> service.getMyCover(10L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("커스텀 표지를 찾을 수 없습니다.");
    }

    @Test
    void updatingKeepsTheOwnerCheckAndTheValueRules() {
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        when(diaryCoverMapper.findByDiaryIdAndUserId(10L, 7L)).thenReturn(cover);

        // 아는 표지 스타일과 #RRGGBB 만 저장된다
        assertThatThrownBy(() -> service.update(10L, 7L, "GOLD_PLATED", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 스타일을 다시 선택해 주세요.");
        assertThatThrownBy(() -> service.update(10L, 7L, "LEATHER_DEEP_GREEN", "red"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 색상을 다시 선택해 주세요.");
        // 저장 SQL 도 userId 를 함께 받아 다이어리와 이어 붙여 확인한다
        verify(diaryCoverMapper, never()).update(any(), any());
    }

    /** 지우고 나면 못 읽으므로, 사진 파일 정리에 쓸 목록을 먼저 확보해 돌려준다. */
    @Test
    void removingACoverReturnsItsElementsForFileCleanup() {
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        when(diaryCoverMapper.findByDiaryIdAndUserId(10L, 7L)).thenReturn(cover);
        when(diaryCoverElementMapper.findAllByCoverId(3L)).thenReturn(List.of());
        when(diaryCoverMapper.deleteByDiaryIdAndUserId(10L, 7L)).thenReturn(1);

        assertThat(service.delete(10L, 7L)).isEmpty();

        var order = org.mockito.Mockito.inOrder(diaryCoverElementMapper, diaryCoverMapper);
        order.verify(diaryCoverElementMapper).findAllByCoverId(3L);
        order.verify(diaryCoverMapper).deleteByDiaryIdAndUserId(10L, 7L);
    }

    /**
     * 디자인을 입힐 때 사진은 파일까지 새로 만들고, 스티커는 공용 asset 이라 경로만 옮긴다.
     * 사진 파일을 나눠 쓰면 한쪽을 지웠을 때 다른 쪽이 깨진다.
     */
    @Test
    void applyingADesignCopiesPhotoFilesButKeepsStickerUrls() {
        givenOwnedDesign(5L, 7L, "LEATHER_DEEP_GREEN", "#123456");
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of(
                designElement("PHOTO", "/uploads/diary-cover-designs/a.jpg"),
                designElement("STICKER", "/images/diary/stickers/tape-center.png")));
        givenInsertedCover(3L);
        when(fileUploadService.copyStoredFile("/uploads/diary-cover-designs/a.jpg",
                "diary-cover-elements")).thenReturn("/uploads/diary-cover-elements/b.jpg");
        when(diaryCoverElementMapper.insert(any())).thenReturn(1);

        service.applyDesign(10L, 5L, 7L);

        ArgumentCaptor<DiaryCoverElement> saved = ArgumentCaptor.forClass(DiaryCoverElement.class);
        verify(diaryCoverElementMapper, org.mockito.Mockito.times(2)).insert(saved.capture());
        assertThat(saved.getAllValues()).extracting(DiaryCoverElement::getImageUrl)
                .containsExactly("/uploads/diary-cover-elements/b.jpg",
                        "/images/diary/stickers/tape-center.png");
        // 스티커는 파일을 복사하지 않는다
        verify(fileUploadService, org.mockito.Mockito.times(1))
                .copyStoredFile(any(), any());
        // 적용본은 표지 번호만 새로 받고 꾸민 값은 그대로 옮겨 온다
        assertThat(saved.getAllValues()).allSatisfy(element -> {
            assertThat(element.getCoverId()).isEqualTo(3L);
            assertThat(element.getPositionX()).isEqualByComparingTo("0.25");
            assertThat(element.getRotation()).isEqualByComparingTo("7.5");
            assertThat(element.getZIndex()).isEqualTo(2);
        });
        assertThat(saved.getAllValues().get(0).getPhotoStyle()).isEqualTo("POLAROID");
    }

    /** 남의 디자인은 표지가 만들어지기 전에 막힌다. */
    @Test
    void aDesignThatIsNotMineIsRejectedBeforeAnyCoverRowIsCreated() {
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.applyDesign(10L, 5L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 디자인을 찾을 수 없습니다.");
        verify(diaryCoverMapper, never()).insert(any());
    }

    /**
     * 요소를 옮기다 실패하면 DB 는 트랜잭션이 되돌리지만 파일은 남는다.
     * 그래서 이번에 새로 만든 복사본만 지우고 오류를 그대로 올린다.
     */
    @Test
    void aFailureInTheMiddleRemovesOnlyTheFilesCopiedThisTime(@TempDir Path uploadRoot)
            throws IOException {
        ReflectionTestUtils.setField(service, "uploadPath", uploadRoot.toString());
        Path copied = uploadRoot.resolve("diary-cover-elements").resolve("copied.jpg");
        Files.createDirectories(copied.getParent());
        Files.createFile(copied);
        Path original = uploadRoot.resolve("diary-cover-designs").resolve("a.jpg");
        Files.createDirectories(original.getParent());
        Files.createFile(original);

        givenOwnedDesign(5L, 7L, "LEATHER_DEEP_GREEN", null);
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of(
                designElement("PHOTO", "/uploads/diary-cover-designs/a.jpg"),
                designElement("PHOTO", "/uploads/diary-cover-designs/b.jpg")));
        givenInsertedCover(3L);
        when(fileUploadService.copyStoredFile(any(), any()))
                .thenReturn("/uploads/diary-cover-elements/copied.jpg");
        // 두 번째 요소를 저장하지 못하는 상황
        when(diaryCoverElementMapper.insert(any())).thenReturn(1).thenReturn(0);

        assertThatThrownBy(() -> service.applyDesign(10L, 5L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지를 적용하지 못했습니다.");

        // 이번에 만든 복사본만 지우고, 원본 디자인의 사진은 그대로 둔다
        assertThat(copied).doesNotExist();
        assertThat(original).exists();
    }

    /** 다이어리와 표지는 한 번에 만들어진다. 다이어리만 남는 상태를 허용하지 않는다. */
    @Test
    void creatingWithADesignMakesTheDiaryAndThenAppliesTheCover() {
        Diary created = new Diary();
        created.setId(10L);
        when(diaryService.create(7L, created)).thenReturn(created);
        givenOwnedDesign(5L, 7L, "LEATHER_DEEP_GREEN", null);
        when(diaryCoverDesignElementService.getElements(5L, 7L)).thenReturn(List.of());
        givenInsertedCover(3L);

        assertThat(service.createWithDesign(7L, created, 5L)).isSameAs(created);

        var order = org.mockito.Mockito.inOrder(diaryService, diaryCoverMapper);
        order.verify(diaryService).create(7L, created);
        order.verify(diaryCoverMapper).insert(any());
    }

    /** 목록은 카드마다 묻지 않는다. 표지도 요소도 각각 한 번씩만 읽는다. */
    @Test
    void theListReadsCoversAndElementsInOneQueryEach() {
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        when(diaryCoverMapper.findAllByDiaryIds(List.of(10L, 11L), 7L)).thenReturn(List.of(cover));

        var covers = service.findCoversByDiary(List.of(10L, 11L), 7L);
        // 커스텀 표지가 없는 다이어리는 결과에 아예 없다 (그 카드는 기본 표지를 그린다)
        assertThat(covers).containsOnlyKeys(10L);

        DiaryCoverElement element = new DiaryCoverElement();
        element.setCoverId(3L);
        when(diaryCoverElementMapper.findAllByCoverIds(List.of(3L))).thenReturn(List.of(element));

        assertThat(service.findElementsByCover(covers.values()).get(3L)).hasSize(1);
        verify(diaryCoverElementMapper, never()).findAllByCoverId(any());
    }

    /** 빈 목록은 아예 묻지 않는다. */
    @Test
    void anEmptyListAsksNothing() {
        assertThat(service.findCoversByDiary(List.of(), 7L)).isEmpty();
        assertThat(service.findElementsByCover(List.of())).isEmpty();
        verify(diaryCoverMapper, never()).findAllByDiaryIds(any(), any());
        verify(diaryCoverElementMapper, never()).findAllByCoverIds(any());
    }

    private void givenOwnedDesign(Long designId, Long userId, String style, String color) {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(designId);
        design.setUserId(userId);
        design.setBaseCoverStyle(style);
        design.setBackgroundColor(color);
        when(diaryCoverDesignService.getMyDesign(designId, userId)).thenReturn(design);
    }

    /** insert 는 PK 를 인자에 채워 준다. (useGeneratedKeys) */
    private void givenInsertedCover(Long coverId) {
        when(diaryCoverMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCover.class).setId(coverId);
            return 1;
        });
    }

    private DiaryCoverDesignElement designElement(String type, String imageUrl) {
        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setElementType(type);
        element.setImageUrl(imageUrl);
        element.setPhotoStyle("PHOTO".equals(type) ? "POLAROID" : null);
        element.setPositionX(new BigDecimal("0.2500"));
        element.setPositionY(new BigDecimal("0.4000"));
        element.setWidth(new BigDecimal("0.3000"));
        element.setHeight(new BigDecimal("0.2000"));
        element.setRotation(new BigDecimal("7.50"));
        element.setZIndex(2);
        return element;
    }
}
