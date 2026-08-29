package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverMaterial;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryCoverDesignServiceImpl implements DiaryCoverDesignService {

    private final DiaryCoverDesignMapper diaryCoverDesignMapper;
    private final DiaryCoverDesignElementMapper diaryCoverDesignElementMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DiaryCoverDesign> getMyDesigns(Long userId) {
        DiaryCoverValues.requireUser(userId);
        return diaryCoverDesignMapper.findAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCoverDesign getMyDesign(Long designId, Long userId) {
        return requireOwnedDesign(designId, userId);
    }

    @Override
    @Transactional
    public DiaryCoverDesign create(Long userId, DiaryCoverDesign design) {
        DiaryCoverValues.requireUser(userId);
        if (design == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 디자인 정보를 입력해 주세요.");
        }

        DiaryCoverDesign prepared = new DiaryCoverDesign();
        // 소유자는 요청 값을 믿지 않고 현재 사용자로 설정한다.
        prepared.setUserId(userId);
        prepared.setName(DiaryCoverValues.name(design.getName()));
        prepared.setBaseCoverStyle(DiaryCoverValues.baseCoverStyle(design.getBaseCoverStyle()));
        prepared.setBackgroundColor(DiaryCoverValues.backgroundColor(design.getBackgroundColor()));

        if (diaryCoverDesignMapper.insert(prepared) != 1 || prepared.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "표지 디자인을 저장하지 못했습니다.");
        }
        return requireOwnedDesign(prepared.getId(), userId);
    }

    @Override
    @Transactional
    public DiaryCoverDesign updateBasics(Long designId, Long userId,
                                         String name, String baseCoverStyle, String backgroundColor) {
        DiaryCoverDesign existing = requireOwnedDesign(designId, userId);
        // 셋 다 통과한 뒤에 한 번만 저장한다. (하나라도 틀리면 아무것도 바뀌지 않는다)
        existing.setName(DiaryCoverValues.name(name));
        /*
          화면은 재질 세 갈래만 보여 주고 색은 따로 고른다.
          재질이 그대로면 쓰던 값을 그대로 둔다 — "가죽 딥그린"으로 만들어 둔 디자인이
          이름만 고쳤다고 대표 가죽색으로 바뀌지 않게 하려는 것이다.
        */
        existing.setBaseCoverStyle(DiaryCoverMaterial.resolveCoverStyle(
                existing.getBaseCoverStyle(),
                DiaryCoverValues.baseCoverStyle(baseCoverStyle)));
        existing.setBackgroundColor(DiaryCoverValues.backgroundColor(backgroundColor));
        return save(existing, userId);
    }

    @Override
    @Transactional
    public DiaryCoverDesign rename(Long designId, Long userId, String name) {
        DiaryCoverDesign existing = requireOwnedDesign(designId, userId);
        existing.setName(DiaryCoverValues.name(name));
        return save(existing, userId);
    }

    @Override
    @Transactional
    public DiaryCoverDesign changeBaseCoverStyle(Long designId, Long userId, String baseCoverStyle) {
        DiaryCoverDesign existing = requireOwnedDesign(designId, userId);
        existing.setBaseCoverStyle(DiaryCoverValues.baseCoverStyle(baseCoverStyle));
        return save(existing, userId);
    }

    @Override
    @Transactional
    public DiaryCoverDesign changeBackgroundColor(Long designId, Long userId, String backgroundColor) {
        DiaryCoverDesign existing = requireOwnedDesign(designId, userId);
        existing.setBackgroundColor(DiaryCoverValues.backgroundColor(backgroundColor));
        return save(existing, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaryCoverDesignElement> getElements(Long designId, Long userId) {
        DiaryCoverDesign design = requireOwnedDesign(designId, userId);
        return diaryCoverDesignElementMapper.findAllByDesignId(design.getId());
    }

    @Override
    @Transactional
    public List<DiaryCoverDesignElement> delete(Long designId, Long userId) {
        DiaryCoverDesign existing = requireOwnedDesign(designId, userId);
        // 지우고 나면 못 읽으므로, 사진 파일 정리에 쓸 목록을 먼저 확보한다.
        List<DiaryCoverDesignElement> elements =
                diaryCoverDesignElementMapper.findAllByDesignId(existing.getId());

        if (diaryCoverDesignMapper.deleteByIdAndUserId(designId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다.");
        }
        return elements;
    }

    /** 바뀐 값 하나만 담아 저장한다. 소유자와 대상은 검증된 값으로 고정한다. */
    private DiaryCoverDesign save(DiaryCoverDesign design, Long userId) {
        if (diaryCoverDesignMapper.update(design) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다.");
        }
        return requireOwnedDesign(design.getId(), userId);
    }

    /**
     * 본인 소유 디자인만 통과시킨다.
     * 남의 디자인 번호를 넣어도 찾지 못하므로, 있음/없음이 새어 나가지 않게 404 로 돌려준다.
     */
    private DiaryCoverDesign requireOwnedDesign(Long designId, Long userId) {
        DiaryCoverValues.requireUser(userId);
        if (designId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 디자인을 선택해 주세요.");
        }
        DiaryCoverDesign design = diaryCoverDesignMapper.findByIdAndUserId(designId, userId);
        if (design == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다.");
        }
        return design;
    }
}
