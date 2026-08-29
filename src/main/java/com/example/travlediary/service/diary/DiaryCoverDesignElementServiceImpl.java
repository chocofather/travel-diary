package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverPhotoStyle;
import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerKind;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryCoverDesignElementServiceImpl implements DiaryCoverDesignElementService {

    /** 표지에 붙일 수 있는 유형. (NOTE/TEXT 는 다음 단계) */
    private static final String TYPE_STICKER = "STICKER";
    private static final String TYPE_PHOTO = "PHOTO";

    /*
      처음 붙일 때의 자리와 크기. 표지는 세로형이라 페이지보다 조금 작게 둔다.
      값의 뜻과 범위는 페이지 다꾸와 같다. (0~1 상대값, DB CHECK 와 같은 한계)
    */
    private static final BigDecimal PHOTO_SIZE = new BigDecimal("0.34000");
    private static final BigDecimal STICKER_SIZE = new BigDecimal("0.22000");
    private static final BigDecimal TAPE_WIDTH = new BigDecimal("0.52000");
    private static final BigDecimal TAPE_HEIGHT = new BigDecimal("0.08000");
    private static final BigDecimal STICKER_CENTER = new BigDecimal("0.38000");
    /** 같은 자리에 겹쳐 쌓이지 않게 조금씩 어긋나게 놓는다. */
    private static final BigDecimal OFFSET_STEP = new BigDecimal("0.04000");
    private static final int OFFSET_CYCLE = 5;

    private static final BigDecimal DEFAULT_ROTATION = new BigDecimal("0.00");
    /** 좌표/크기/회전 허용 범위 (DB CHECK 제약과 같은 값) */
    private static final BigDecimal POSITION_MIN = new BigDecimal("-0.5");
    private static final BigDecimal POSITION_MAX = new BigDecimal("1.5");
    private static final BigDecimal SIZE_MIN = new BigDecimal("0.01");
    private static final BigDecimal SIZE_MAX = BigDecimal.ONE;
    private static final BigDecimal ROTATION_MIN = new BigDecimal("-360");
    private static final BigDecimal ROTATION_MAX = new BigDecimal("360");

    /** 디자인 소유권은 보관함 서비스가 확인해 준다. (그 규칙을 그대로 따른다) */
    private final DiaryCoverDesignService diaryCoverDesignService;
    private final DiaryCoverDesignElementMapper diaryCoverDesignElementMapper;
    /** 붙일 수 있는 스티커 목록. 페이지 다꾸와 같은 manifest 를 함께 쓴다. */
    private final DiaryStickerCatalog diaryStickerCatalog;

    @Override
    @Transactional(readOnly = true)
    public List<DiaryCoverDesignElement> getElements(Long designId, Long userId) {
        return diaryCoverDesignElementMapper.findAllByDesignId(requireDesignId(designId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<DiaryCoverDesignElement>> getElementsByDesign(List<Long> designIds,
                                                                        Long userId) {
        DiaryCoverValues.requireUser(userId);
        if (designIds == null || designIds.isEmpty()) {
            return Map.of();
        }
        // 한 번만 묻고 디자인 번호로 나눠 담는다. (카드마다 따로 묻지 않는다)
        return diaryCoverDesignElementMapper.findAllByDesignIds(designIds, userId).stream()
                .collect(Collectors.groupingBy(DiaryCoverDesignElement::getDesignId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement createSticker(Long designId, Long userId, String stickerId) {
        Long ownedDesignId = requireDesignId(designId, userId);
        DiarySticker sticker = diaryStickerCatalog.find(stickerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 스티커입니다."));

        List<DiaryCoverDesignElement> placed =
                diaryCoverDesignElementMapper.findAllByDesignId(ownedDesignId);
        BigDecimal offset = OFFSET_STEP.multiply(
                BigDecimal.valueOf(placed.size() % OFFSET_CYCLE));
        boolean tape = DiaryStickerKind.isMaskingTape(sticker.imageUrl());

        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setDesignId(ownedDesignId);
        element.setElementType(TYPE_STICKER);
        // 그림 경로는 요청 값이 아니라 허용 목록에서 고른 값이다.
        element.setImageUrl(sticker.imageUrl());
        element.setPositionX(STICKER_CENTER.add(offset));
        element.setPositionY(STICKER_CENTER.add(offset));
        element.setWidth(tape ? TAPE_WIDTH : STICKER_SIZE);
        element.setHeight(tape ? TAPE_HEIGHT : STICKER_SIZE);
        element.setRotation(DEFAULT_ROTATION);
        // 새로 붙인 것은 늘 맨 위에 올라온다.
        element.setZIndex(nextZIndex(placed));

        if (diaryCoverDesignElementMapper.insert(element) != 1 || element.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "스티커를 붙이지 못했습니다.");
        }
        return requireElement(element.getId(), ownedDesignId);
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement createPhoto(Long designId, Long userId,
                                               String imageUrl, int placedBefore,
                                               String photoStyle) {
        Long ownedDesignId = requireDesignId(designId, userId);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진을 선택해 주세요.");
        }
        if (!DiaryCoverPhotoStyle.isSupported(photoStyle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 모양을 다시 선택해 주세요.");
        }

        List<DiaryCoverDesignElement> placed =
                diaryCoverDesignElementMapper.findAllByDesignId(ownedDesignId);
        /*
          여러 장을 한꺼번에 올려도 정확히 겹쳐 한 장처럼 보이지 않게 조금씩 어긋나게 놓는다.
          이미 붙어 있던 수와 이번에 앞서 올린 수를 함께 세고, 다섯 장마다 처음 자리로 돌아온다.
          어긋나는 폭이 작아 표지 밖으로 밀려나지 않는다. (0.38 + 0.16 + 0.34 < 1)
        */
        BigDecimal offset = OFFSET_STEP.multiply(
                BigDecimal.valueOf((placed.size() + placedBefore) % OFFSET_CYCLE));

        DiaryCoverDesignElement element = new DiaryCoverDesignElement();
        element.setDesignId(ownedDesignId);
        element.setElementType(TYPE_PHOTO);
        element.setImageUrl(imageUrl);
        // 모습은 어느 자리에서 올렸는지로 이미 정해져 있다. (붙인 뒤에 다시 고르지 않는다)
        // 값이 비어 있는 예전 사진만 폴라로이드로 읽고, 그쪽 값은 그대로 둔다.
        element.setPhotoStyle(photoStyle);
        element.setPositionX(STICKER_CENTER.add(offset));
        element.setPositionY(STICKER_CENTER.add(offset));
        element.setWidth(PHOTO_SIZE);
        element.setHeight(PHOTO_SIZE);
        element.setRotation(DEFAULT_ROTATION);
        // 새로 올린 것은 늘 맨 위에, 고른 순서대로 쌓인다.
        element.setZIndex(nextZIndex(placed) + placedBefore);

        if (diaryCoverDesignElementMapper.insert(element) != 1 || element.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "사진을 붙이지 못했습니다.");
        }
        return requireElement(element.getId(), ownedDesignId);
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement move(Long designId, Long elementId, Long userId,
                                        BigDecimal positionX, BigDecimal positionY) {
        Long ownedDesignId = requireDesignId(designId, userId);
        requireElement(elementId, ownedDesignId);

        int changed = diaryCoverDesignElementMapper.updatePosition(elementId, ownedDesignId,
                clamp(positionX, POSITION_MIN, POSITION_MAX, "자리"),
                clamp(positionY, POSITION_MIN, POSITION_MAX, "자리"));
        return applied(changed, elementId, ownedDesignId);
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement resize(Long designId, Long elementId, Long userId,
                                          BigDecimal width, BigDecimal height) {
        Long ownedDesignId = requireDesignId(designId, userId);
        requireElement(elementId, ownedDesignId);

        int changed = diaryCoverDesignElementMapper.updateSize(elementId, ownedDesignId,
                clamp(width, SIZE_MIN, SIZE_MAX, "크기"),
                clamp(height, SIZE_MIN, SIZE_MAX, "크기"));
        return applied(changed, elementId, ownedDesignId);
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement rotate(Long designId, Long elementId, Long userId,
                                          BigDecimal rotation) {
        Long ownedDesignId = requireDesignId(designId, userId);
        requireElement(elementId, ownedDesignId);

        int changed = diaryCoverDesignElementMapper.updateRotation(elementId, ownedDesignId,
                clamp(rotation, ROTATION_MIN, ROTATION_MAX, "각도"));
        return applied(changed, elementId, ownedDesignId);
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement changePhotoStyle(Long designId, Long elementId, Long userId,
                                                    String photoStyle) {
        Long ownedDesignId = requireDesignId(designId, userId);
        DiaryCoverDesignElement existing = requireElement(elementId, ownedDesignId);

        // 사진에만 있는 값이다. 스티커/라벨에는 두지 않는다.
        if (!TYPE_PHOTO.equals(existing.getElementType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 요소가 아닙니다.");
        }
        if (!DiaryCoverPhotoStyle.isSupported(photoStyle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 모양을 다시 선택해 주세요.");
        }

        // 모습 한 칸만 바꾼다. 자리/크기/각도/겹침 순서는 그대로 남는다.
        int changed = diaryCoverDesignElementMapper
                .updatePhotoStyle(elementId, ownedDesignId, photoStyle);
        return applied(changed, elementId, ownedDesignId);
    }

    @Override
    @Transactional
    public List<DiaryCoverDesignElement> changeLayer(Long designId, Long elementId, Long userId,
                                                     boolean forward) {
        Long ownedDesignId = requireDesignId(designId, userId);
        requireElement(elementId, ownedDesignId);

        // 조회 결과가 z_index, id 순서이므로 그대로 겹침 순서로 쓸 수 있다.
        List<DiaryCoverDesignElement> ordered =
                new ArrayList<>(diaryCoverDesignElementMapper.findAllByDesignId(ownedDesignId));
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

        // 0,1,2... 로 다시 번호를 매겨 순서를 안정적으로 정리한다.
        for (int i = 0; i < ordered.size(); i++) {
            DiaryCoverDesignElement element = ordered.get(i);
            if (Objects.equals(element.getZIndex(), i)) {
                continue;
            }
            if (diaryCoverDesignElementMapper.updateLayer(element.getId(), ownedDesignId, i) != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
            }
            element.setZIndex(i);
        }
        return ordered;
    }

    @Override
    @Transactional
    public DiaryCoverDesignElement delete(Long designId, Long elementId, Long userId) {
        Long ownedDesignId = requireDesignId(designId, userId);
        // 지우고 나면 못 읽으므로 무엇을 지웠는지 먼저 확보한다. (사진이면 파일도 정리해야 한다)
        DiaryCoverDesignElement removed = requireElement(elementId, ownedDesignId);

        if (diaryCoverDesignElementMapper.deleteById(elementId, ownedDesignId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
        // 파일 정리는 여기서 하지 않는다. 스티커는 공용 asset 이라 지울 파일이 없고,
        // 사진만 올린 파일이 있으므로 호출한 쪽이 유형을 보고 정리한다.
        return removed;
    }

    /** 맨 위 자리. 비어 있으면 0 부터 시작한다. */
    private int nextZIndex(List<DiaryCoverDesignElement> placed) {
        int top = -1;
        for (DiaryCoverDesignElement element : placed) {
            if (element.getZIndex() != null && element.getZIndex() > top) {
                top = element.getZIndex();
            }
        }
        return top + 1;
    }

    /** 본인 디자인인지 확인하고 그 번호를 돌려준다. (남의 designId 는 여기서 막힌다) */
    private Long requireDesignId(Long designId, Long userId) {
        DiaryCoverDesign design = diaryCoverDesignService.getMyDesign(designId, userId);
        return design.getId();
    }

    /** 그 디자인의 요소인지 확인한다. */
    private DiaryCoverDesignElement requireElement(Long elementId, Long designId) {
        if (elementId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요소를 선택해 주세요.");
        }
        DiaryCoverDesignElement element =
                diaryCoverDesignElementMapper.findById(elementId, designId);
        if (element == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
        return element;
    }

    private DiaryCoverDesignElement applied(int changed, Long elementId, Long designId) {
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다.");
        }
        return requireElement(elementId, designId);
    }

    /** DB CHECK 와 같은 범위 밖의 값은 저장 전에 막는다. */
    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max, String what) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " 값을 확인해 주세요.");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " 값을 확인해 주세요.");
        }
        return value;
    }
}
