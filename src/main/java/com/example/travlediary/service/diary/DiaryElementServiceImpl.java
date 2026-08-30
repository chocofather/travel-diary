package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryNoteColor;
import com.example.travlediary.model.DiaryNoteStyle;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryElementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DiaryElementServiceImpl implements DiaryElementService {

    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_PHOTO = "PHOTO";
    private static final String TYPE_STICKER = "STICKER";
    /**
     * 사진과 스티커는 같은 자유배치 이미지 요소다.
     * (image_url 만 쓰고 text_content 는 비운다 — DB payload CHECK 와 같은 규칙)
     */
    private static final Set<String> IMAGE_TYPES = Set.of(TYPE_PHOTO, TYPE_STICKER);
    /** 라벨·떡메모지. 글과 디자인을 함께 들고 다니는 한 덩어리 요소다. */
    private static final String TYPE_NOTE = "NOTE";
    /** 저장할 수 있는 요소 유형. (DB chk_diary_elements_type 과 같은 값) */
    private static final Set<String> ALLOWED_TYPES =
            Set.of(TYPE_TEXT, TYPE_PHOTO, TYPE_STICKER, TYPE_NOTE);
    /** 라벨은 한 줄짜리 작은 딱지다. 길게 적을 자리가 아니다. */
    private static final int LABEL_TEXT_MAX = 100;
    /**
     * 라벨기로 붙이는 글씨. 종이 배경 없이 글자만 놓는 짧은 문구라 NOTE 라벨보다 더 짧게 둔다.
     * (긴 글은 페이지 본문이나 떡메모지가 맡는다)
     */
    private static final int TEXT_LABEL_MAX = 50;
    /** 라벨기 글자색은 #RRGGBB 만 저장한다. (text_color VARCHAR(7)) */
    private static final Pattern LABEL_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /**
     * 라벨기 글씨의 처음 크기. 한 줄짜리 문구라 낮고 넓다.
     * 높이는 화면에서 조절할 수 있는 가장 짧은 길이(0.08)에 맞춰 둔다 —
     * 그보다 낮게 두면 크기를 잡는 순간 높이가 튄다.
     */
    private static final BigDecimal TEXT_LABEL_WIDTH = new BigDecimal("0.32000");
    private static final BigDecimal TEXT_LABEL_HEIGHT = new BigDecimal("0.08000");
    /** 처음 놓는 자리. 스티커·라벨과 같은 규칙으로 조금씩 어긋나게 쌓는다. */
    private static final BigDecimal TEXT_LABEL_CENTER = new BigDecimal("0.34000");
    private static final BigDecimal TEXT_LABEL_OFFSET_STEP = new BigDecimal("0.04000");
    private static final int TEXT_LABEL_OFFSET_CYCLE = 5;
    /** 떡메모지는 여러 줄을 적는 자리라 넉넉히 둔다. */
    private static final int MEMO_TEXT_MAX = 1000;

    /** 좌표/크기 기본값과 허용 범위 (DB 기본값·CHECK 제약과 같은 값) */
    private static final BigDecimal DEFAULT_POSITION = new BigDecimal("0.00000");
    private static final BigDecimal DEFAULT_SIZE = new BigDecimal("0.30000");
    private static final BigDecimal DEFAULT_ROTATION = new BigDecimal("0.00");
    private static final int DEFAULT_Z_INDEX = 0;
    private static final BigDecimal POSITION_MIN = new BigDecimal("-0.5");
    private static final BigDecimal POSITION_MAX = new BigDecimal("1.5");
    private static final BigDecimal SIZE_MAX = BigDecimal.ONE;
    private static final BigDecimal ROTATION_MIN = new BigDecimal("-360");
    private static final BigDecimal ROTATION_MAX = new BigDecimal("360");

    private final DiaryPageService diaryPageService;
    private final DiaryElementMapper diaryElementMapper;
    /** 라벨·떡메모지 디자인 허용 목록. 아는 값인지 확인하는 데만 쓴다. */
    private final DiaryNoteCatalog diaryNoteCatalog;
    /** 라벨기 글꼴 허용 목록. 표지 편집과 같은 목록을 함께 쓴다. */
    private final DiaryLabelFontCatalog diaryLabelFontCatalog;

    @Override
    @Transactional(readOnly = true)
    public List<DiaryElement> getElements(Long diaryId, Long pageId, Long userId) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        return diaryElementMapper.findByPageId(page.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryElement getElement(Long diaryId, Long pageId, Long elementId, Long userId) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        return requireElementOfPage(elementId, page.getId());
    }

    @Override
    @Transactional
    public DiaryElement create(Long diaryId, Long pageId, Long userId, DiaryElement element) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement prepared = validated(element, requireElementType(element));
        // 대상 페이지는 요청 값을 믿지 않고 검증된 pageId 로 설정한다.
        prepared.setPageId(page.getId());

        if (diaryElementMapper.insert(prepared) != 1 || prepared.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "요소를 저장하지 못했습니다.");
        }
        return requireElementOfPage(prepared.getId(), page.getId());
    }

    @Override
    @Transactional
    public DiaryElement createLabel(Long diaryId, Long pageId, Long userId,
                                    String text, String textFont, String textColor) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);

        // 스티커·떡메모지와 같은 규칙으로 조금씩 어긋나게 놓는다. (새 좌표 계산을 만들지 않는다)
        int placed = diaryElementMapper.findByPageId(page.getId()).size();
        BigDecimal offset = TEXT_LABEL_OFFSET_STEP
                .multiply(BigDecimal.valueOf(placed % TEXT_LABEL_OFFSET_CYCLE));

        DiaryElement element = new DiaryElement();
        element.setElementType(TYPE_TEXT);
        // 글·글꼴·글자색 검증은 create 가 타는 validated() 한 곳에서 한다.
        element.setTextContent(text);
        element.setTextFont(textFont);
        element.setTextColor(textColor);
        element.setPositionX(TEXT_LABEL_CENTER.add(offset));
        element.setPositionY(TEXT_LABEL_CENTER.add(offset));
        element.setWidth(TEXT_LABEL_WIDTH);
        element.setHeight(TEXT_LABEL_HEIGHT);
        // 회전 0 / 겹침 순서는 사진·스티커와 같은 기본값을 쓴다.
        return create(diaryId, pageId, userId, element);
    }

    @Override
    @Transactional
    public void deleteLabel(Long diaryId, Long pageId, Long elementId, Long userId) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());
        // 다른 유형의 요소 번호를 보내도 여기서 걸린다. (사진을 이 문으로 지우지 못한다)
        if (!TYPE_TEXT.equals(existing.getElementType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "글씨 요소가 아닙니다.");
        }
        // 글씨는 파일을 갖지 않으므로 DB 행만 지운다.
        delete(diaryId, pageId, elementId, userId);
    }

    @Override
    @Transactional
    public DiaryElement update(Long diaryId, Long pageId, Long elementId, Long userId,
                               DiaryElement element) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());
        // 유형은 기존 요소의 값을 유지한다. (TEXT ↔ PHOTO ↔ STICKER ↔ NOTE 전환 없음)
        DiaryElement prepared = validated(element, existing.getElementType());
        // 대상 요소/페이지는 요청 값이 아니라 검증된 값으로 고정한다. (페이지 이동 없음)
        prepared.setId(existing.getId());
        prepared.setPageId(page.getId());

        if (diaryElementMapper.update(prepared) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
        return requireElementOfPage(existing.getId(), page.getId());
    }

    /**
     * 라벨/떡메모지의 글만 바꾼다.
     *
     * <p>화면은 글 하나만 보낸다. 디자인·자리·크기·회전·겹침 순서는 저장된 값에서 그대로 온다.
     * 다른 유형의 요소는 이 문으로 들어올 수 없다 — 사진에 글을 붙이거나
     * 스티커의 그림을 지우는 길이 생기지 않게 한다.
     */
    @Override
    @Transactional
    public DiaryElement updateNoteText(Long diaryId, Long pageId, Long elementId, Long userId,
                                       String textContent) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());
        if (!TYPE_NOTE.equals(existing.getElementType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "라벨/메모지 요소가 아닙니다.");
        }

        // 글만 바꾸고 나머지 값은 기존 요소에서 그대로 가져간다.
        DiaryElement retyped = copyOf(existing);
        retyped.setTextContent(noteText(existing, textContent));
        return update(diaryId, pageId, elementId, userId, retyped);
    }

    /**
     * 라벨/떡메모지에 적을 수 있는 글로 다듬는다.
     *
     * <p>길이는 갈래에 따라 다르다. 라벨은 한 줄짜리 작은 딱지라 짧고,
     * 떡메모지는 여러 줄을 적는 자리라 길다. 어느 쪽도 빈 글은 그대로 둔다.
     *
     * <p>라벨에 줄바꿈이 들어오면(붙여넣기 등) 막지 않고 한 칸으로 바꾼다.
     * 붙여넣기가 통째로 거절되는 것보다 한 줄로 들어오는 편이 덜 놀랍다.
     */
    private String noteText(DiaryElement existing, String textContent) {
        boolean label = DiaryNoteStyle.CATEGORY_LABEL.equals(
                diaryNoteCatalog.find(existing.getStyleType())
                        .map(DiaryNoteStyle::category)
                        .orElse(DiaryNoteStyle.CATEGORY_MEMO));

        String text = textContent == null ? "" : textContent.replace("\r\n", "\n");
        if (label) {
            text = text.replace('\n', ' ');
        }
        text = text.strip();

        int limit = label ? LABEL_TEXT_MAX : MEMO_TEXT_MAX;
        if (text.length() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    limit + "자까지 입력할 수 있습니다.");
        }
        return text;
    }

    /**
     * 라벨기로 붙일 문구를 다듬는다.
     *
     * <p>한 줄짜리 짧은 문구다. 줄바꿈이 들어오면(붙여넣기 등) 막지 않고 한 칸으로 바꾼다 —
     * 붙여넣기가 통째로 거절되는 것보다 한 줄로 들어오는 편이 덜 놀랍다.
     * 빈 문구는 붙일 것이 없다는 뜻이라 막는다. (NOTE 와 다른 점이다)
     */
    private String labelText(String textContent) {
        String text = (textContent == null ? "" : textContent)
                .replace("\r\n", "\n").replace('\n', ' ').strip();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
        }
        if (text.length() > TEXT_LABEL_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    TEXT_LABEL_MAX + "자까지 입력할 수 있습니다.");
        }
        return text;
    }

    /**
     * 라벨기 글꼴. 목록에 있는 값만 저장한다. (그대로 화면의 class 가 되는 값이다)
     * 고르지 않은 채로 오면 NULL 로 두고 화면이 기본 글꼴로 그린다.
     */
    private String labelFont(String textFont) {
        if (textFont == null || textFont.isBlank()) {
            return null;
        }
        return diaryLabelFontCatalog.find(textFont)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 글꼴입니다."))
                .code();
    }

    /**
     * 라벨기 글자색. #RRGGBB 만 저장한다. (그대로 화면의 색이 되는 값이라 형식을 가린다)
     * 고르지 않은 채로 오면 NULL 로 두고 화면이 기본 먹색으로 그린다.
     */
    private String labelColor(String textColor) {
        if (textColor == null || textColor.isBlank()) {
            return null;
        }
        String color = textColor.strip();
        if (!LABEL_COLOR.matcher(color).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "글자색을 다시 선택해 주세요.");
        }
        return color;
    }

    @Override
    @Transactional
    public DiaryElement move(Long diaryId, Long pageId, Long elementId, Long userId,
                             BigDecimal positionX, BigDecimal positionY) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());

        DiaryElement moved = copyOf(existing);
        moved.setPositionX(positionX);
        moved.setPositionY(positionY);
        return update(diaryId, pageId, elementId, userId, moved);
    }

    @Override
    @Transactional
    public DiaryElement resize(Long diaryId, Long pageId, Long elementId, Long userId,
                               BigDecimal width, BigDecimal height) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());

        // 크기만 바꾸고 나머지 값은 기존 요소에서 그대로 가져간다.
        DiaryElement resized = copyOf(existing);
        resized.setWidth(width);
        resized.setHeight(height);
        return update(diaryId, pageId, elementId, userId, resized);
    }

    @Override
    @Transactional
    public DiaryElement rotate(Long diaryId, Long pageId, Long elementId, Long userId,
                               BigDecimal rotation) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        DiaryElement existing = requireElementOfPage(elementId, page.getId());

        // 회전만 바꾸고 나머지 값은 기존 요소에서 그대로 가져간다.
        DiaryElement rotated = copyOf(existing);
        rotated.setRotation(rotation);
        return update(diaryId, pageId, elementId, userId, rotated);
    }

    @Override
    @Transactional
    public List<DiaryElement> changeLayer(Long diaryId, Long pageId, Long elementId, Long userId,
                                          boolean forward) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        requireElementOfPage(elementId, page.getId());

        // 조회 결과는 z_index, id 순서이므로 그대로 겹침 순서로 쓸 수 있다.
        List<DiaryElement> ordered = new ArrayList<>(diaryElementMapper.findByPageId(page.getId()));
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (Objects.equals(ordered.get(i).getId(), elementId)) {
                index = i;
                break;
            }
        }

        int target = forward ? index + 1 : index - 1;
        if (index < 0 || target < 0 || target >= ordered.size()) {
            // 이미 맨 앞/맨 뒤면 값을 바꾸지 않는다.
            return ordered;
        }
        Collections.swap(ordered, index, target);

        // 페이지 요소를 0,1,2... 로 다시 번호 매겨 순서를 안정적으로 정리한다.
        for (int i = 0; i < ordered.size(); i++) {
            DiaryElement element = ordered.get(i);
            if (Objects.equals(element.getZIndex(), i)) {
                continue;
            }
            DiaryElement renumbered = copyOf(element);
            renumbered.setId(element.getId());
            renumbered.setPageId(page.getId());
            renumbered.setZIndex(i);
            if (diaryElementMapper.update(renumbered) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
            }
            element.setZIndex(i);
        }
        return ordered;
    }

    @Override
    @Transactional
    public void delete(Long diaryId, Long pageId, Long elementId, Long userId) {
        DiaryPage page = requireOwnedPage(diaryId, pageId, userId);
        requireElementOfPage(elementId, page.getId());
        if (diaryElementMapper.delete(elementId, page.getId()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
    }

    /**
     * 일부 값만 바꿔 저장할 때 쓰는 복사본.
     * (유형·내용·그림·디자인·좌표·크기·회전·겹침 순서를 그대로 옮긴다)
     *
     * <p>저장할 때 쓰는 칸을 하나라도 빠뜨리면 그 값이 조용히 비워진 채로 검사를 지나간다.
     * 옮기기·크기·회전·겹침 순서가 모두 이 복사본을 거치므로,
     * diary_elements 에 칸이 늘면 여기도 함께 늘려야 한다.
     */
    private DiaryElement copyOf(DiaryElement existing) {
        DiaryElement copy = new DiaryElement();
        copy.setElementType(existing.getElementType());
        copy.setTextContent(existing.getTextContent());
        copy.setImageUrl(existing.getImageUrl());
        copy.setStyleType(existing.getStyleType());
        copy.setColorType(existing.getColorType());
        copy.setPhotoStyle(existing.getPhotoStyle());
        copy.setTextFont(existing.getTextFont());
        copy.setTextColor(existing.getTextColor());
        copy.setPositionX(existing.getPositionX());
        copy.setPositionY(existing.getPositionY());
        copy.setWidth(existing.getWidth());
        copy.setHeight(existing.getHeight());
        copy.setRotation(existing.getRotation());
        copy.setZIndex(existing.getZIndex());
        return copy;
    }

    /** 부모 다이어리 소유권과 페이지 소속을 함께 확인한다. */
    private DiaryPage requireOwnedPage(Long diaryId, Long pageId, Long userId) {
        return diaryPageService.getPage(diaryId, pageId, userId);
    }

    /** 그 요소가 해당 페이지에 속하는지 확인한다. (없는 요소와 다른 경로의 요소를 같은 404 로 처리) */
    private DiaryElement requireElementOfPage(Long elementId, Long pageId) {
        DiaryElement element = elementId == null
                ? null
                : diaryElementMapper.findByIdAndPageId(elementId, pageId);
        if (element == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
        return element;
    }

    /** 생성 시 요청한 유형. TEXT/PHOTO/STICKER/NOTE 만 허용한다. */
    private String requireElementType(DiaryElement element) {
        if (element == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요소 정보를 입력해 주세요.");
        }
        String elementType = element.getElementType() == null
                ? "" : element.getElementType().strip();
        if (!ALLOWED_TYPES.contains(elementType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 요소 유형입니다.");
        }
        return elementType;
    }

    /**
     * 저장 전 입력값 검증. 원본을 건드리지 않고 정리된 값을 담은 DiaryElement 를 돌려준다.
     * 좌표/크기는 DB CHECK 와 같은 범위로 먼저 확인해 DB 예외가 500 으로 나가지 않게 한다.
     */
    private DiaryElement validated(DiaryElement element, String elementType) {
        if (element == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요소 정보를 입력해 주세요.");
        }

        DiaryElement prepared = new DiaryElement();
        prepared.setElementType(elementType);
        applyPayload(prepared, element, elementType);

        prepared.setPositionX(position(element.getPositionX(), "가로 위치"));
        prepared.setPositionY(position(element.getPositionY(), "세로 위치"));
        prepared.setWidth(size(element.getWidth(), "너비"));
        prepared.setHeight(size(element.getHeight(), "높이"));
        prepared.setRotation(rotation(element.getRotation()));
        prepared.setZIndex(zIndex(element.getZIndex()));
        return prepared;
    }

    /**
     * TEXT 는 본문만, PHOTO/STICKER 는 이미지 경로만, NOTE 는 본문과 디자인을 남긴다.
     * (DB payload CHECK 와 같은 규칙) 사진과 스티커는 같은 이미지 요소라 검증도 한 곳에서 공유한다.
     *
     * <p>{@code prepared} 는 갓 만든 빈 요소다. 여기서 채운 값만 저장되므로,
     * 유형에 맞지 않는 칸(예: 사진의 style_type)은 손대지 않는 것만으로 NULL 이 된다.
     */
    private void applyPayload(DiaryElement prepared, DiaryElement element, String elementType) {
        String textContent = element.getTextContent() == null
                ? "" : element.getTextContent().strip();
        String imageUrl = element.getImageUrl() == null ? "" : element.getImageUrl().strip();

        if (TYPE_TEXT.equals(elementType)) {
            if (textContent.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용을 입력해 주세요.");
            }
            prepared.setTextContent(labelText(textContent));
            // 글꼴·글자색은 아는 값일 때만 남긴다. 비어 있으면 화면이 기본값으로 그린다.
            prepared.setTextFont(labelFont(element.getTextFont()));
            prepared.setTextColor(labelColor(element.getTextColor()));
            prepared.setImageUrl(null);
            return;
        }

        if (TYPE_NOTE.equals(elementType)) {
            /*
              붙인 직후에는 아직 적은 글이 없다.
              여기서 글을 강요하면 라벨을 붙이기도 전에 무슨 말을 쓸지 정해야 한다.
              그래서 빈 글은 그대로 두고, DB 가 막는 NULL 만 빈 문자열로 맞춘다.
            */
            prepared.setTextContent(textContent);
            String styleType = requireNoteStyle(element.getStyleType());
            prepared.setStyleType(styleType);
            prepared.setColorType(noteColor(element.getColorType()));
            prepared.setImageUrl(null);
            return;
        }

        if (!IMAGE_TYPES.contains(elementType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 요소 유형입니다.");
        }
        if (imageUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    TYPE_STICKER.equals(elementType) ? "스티커를 선택해 주세요." : "사진을 선택해 주세요.");
        }
        prepared.setImageUrl(imageUrl);
        prepared.setTextContent(null);
        /*
          사진의 모습(일반/폴라로이드)은 PHOTO 만 쓴다. 스티커는 늘 비운다.
          고르지 않고 들어온 사진은 비워 두고 읽을 때 폴라로이드로 본다 —
          이 칸이 생기기 전에 붙여 둔 사진과 같은 모습이 된다. (표지 사진과 같은 규칙)
        */
        if (TYPE_PHOTO.equals(elementType)) {
            prepared.setPhotoStyle(photoStyle(element.getPhotoStyle()));
        }
    }

    /** 사진의 모습. 아는 값(FULL / POLAROID)만 저장한다. 고르지 않았으면 비워 둔다. */
    private String photoStyle(String photoStyle) {
        if (photoStyle == null || photoStyle.isBlank()) {
            return null;
        }
        if (!DiaryCoverPhotoStyle.isSupported(photoStyle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 모양을 다시 선택해 주세요.");
        }
        return DiaryCoverPhotoStyle.of(photoStyle).getCode();
    }

    /**
     * 라벨/떡메모지 디자인. 목록(diary_notes.json)에 있는 값만 저장된다.
     * 화면이 보낸 값을 그대로 믿지 않는다. 모르는 값이 들어오면 화면이 그릴 모양이 없다.
     */
    private String requireNoteStyle(String styleType) {
        return diaryNoteCatalog.find(styleType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "라벨/메모지 디자인을 선택해 주세요."))
                .code();
    }

    /**
     * 라벨/떡메모지의 색. 모양과 다른 축이라 따로 확인한다.
     *
     * <p>고르지 않았으면 기본색(IVORY)을 실제로 저장한다. 라벨과 떡메모지가 같은 규칙을 쓴다.
     * 색 칸이 생기기 전에 만든 행(NULL)은 고쳐 쓰지 않고 읽을 때만 같은 기본색으로 본다.
     */
    private String noteColor(String colorType) {
        String requested = colorType == null ? "" : colorType.strip();
        if (requested.isEmpty()) {
            return DiaryNoteColor.DEFAULT_CODE;
        }
        return diaryNoteCatalog.findColor(requested)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "라벨/메모지 색을 선택해 주세요."))
                .code();
    }

    private BigDecimal position(BigDecimal value, String label) {
        BigDecimal safeValue = value == null ? DEFAULT_POSITION : value;
        if (safeValue.compareTo(POSITION_MIN) < 0 || safeValue.compareTo(POSITION_MAX) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, label + "가 허용 범위를 벗어났습니다.");
        }
        return safeValue;
    }

    private BigDecimal size(BigDecimal value, String label) {
        BigDecimal safeValue = value == null ? DEFAULT_SIZE : value;
        if (safeValue.compareTo(BigDecimal.ZERO) <= 0 || safeValue.compareTo(SIZE_MAX) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, label + "가 허용 범위를 벗어났습니다.");
        }
        return safeValue;
    }

    private BigDecimal rotation(BigDecimal value) {
        BigDecimal safeValue = value == null ? DEFAULT_ROTATION : value;
        if (safeValue.compareTo(ROTATION_MIN) < 0 || safeValue.compareTo(ROTATION_MAX) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "회전 각도가 허용 범위를 벗어났습니다.");
        }
        return safeValue;
    }

    private int zIndex(Integer value) {
        int safeValue = value == null ? DEFAULT_Z_INDEX : value;
        if (safeValue < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "겹침 순서는 0 이상이어야 합니다.");
        }
        return safeValue;
    }
}
