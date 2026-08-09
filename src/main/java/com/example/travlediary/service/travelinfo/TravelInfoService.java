package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TravelInfoService {

    private final TravelInfoMapper travelInfoMapper;
    private final InfoCategoryMapper infoCategoryMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Transactional(readOnly = true)
    public List<AdminTravelInfoListItemDto> getAdminList(TravelInfoScope scope,
                                                         TravelInfoContentType contentType,
                                                         Long categoryId) {
        return travelInfoMapper.findAdminList(scope, contentType, categoryId);
    }

    @Transactional(readOnly = true)
    public TravelInfo getById(Long id) {
        return requireTravelInfo(travelInfoMapper.findById(id));
    }

    @Transactional(readOnly = true)
    public AdminTravelInfoDetailDto getAdminDetail(Long id) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findById(id));
        InfoCategory category = infoCategoryMapper.findById(travelInfo.getCategoryId());
        List<InfoPeriod> periods = travelInfo.getContentType() == TravelInfoContentType.FESTIVAL
                ? travelInfoMapper.findPeriodsByInfoId(id)
                : List.of();

        AdminTravelInfoDetailDto detail = new AdminTravelInfoDetailDto();
        detail.setId(travelInfo.getId());
        detail.setTitle(travelInfo.getTitle());
        detail.setContent(postContentSanitizer.sanitize(travelInfo.getContent()));
        detail.setScope(travelInfo.getScope());
        detail.setContentType(travelInfo.getContentType());
        detail.setCategoryId(travelInfo.getCategoryId());
        detail.setCategoryName(category == null ? null : category.getName());
        detail.setViews(travelInfo.getViews());
        detail.setCreatedAt(travelInfo.getCreatedAt());
        detail.setUpdatedAt(travelInfo.getUpdatedAt());
        detail.setPeriods(periods == null ? List.of() : periods);
        return detail;
    }

    @Transactional(readOnly = true)
    public TravelInfoForm getForm(Long id) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findById(id));
        travelInfo.setContent(postContentSanitizer.sanitize(travelInfo.getContent()));
        List<InfoPeriod> periods = travelInfoMapper.findPeriodsByInfoId(id);
        return TravelInfoForm.from(travelInfo, periods);
    }

    @Transactional
    public Long create(TravelInfoForm form, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }

        ValidatedTravelInfo validated = validate(form);
        TravelInfo travelInfo = new TravelInfo();
        travelInfo.setTitle(validated.title());
        travelInfo.setContent(validated.content());
        travelInfo.setScope(form.getScope());
        travelInfo.setContentType(form.getContentType());
        travelInfo.setCategoryId(form.getCategoryId());
        travelInfo.setViews(0);
        travelInfo.setUserId(userId);

        if (travelInfoMapper.insertTravelInfo(travelInfo) != 1 || travelInfo.getId() == null) {
            throw new IllegalStateException("여행정보 저장에 실패했습니다.");
        }
        insertPeriods(travelInfo.getId(), validated.periods());
        return travelInfo.getId();
    }

    @Transactional
    public void update(Long id, TravelInfoForm form) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findByIdForUpdate(id));
        ValidatedTravelInfo validated = validate(form);

        travelInfo.setTitle(validated.title());
        travelInfo.setContent(validated.content());
        travelInfo.setScope(form.getScope());
        travelInfo.setContentType(form.getContentType());
        travelInfo.setCategoryId(form.getCategoryId());

        if (travelInfoMapper.updateTravelInfo(travelInfo) != 1) {
            throw notFound();
        }
        travelInfoMapper.deletePeriodsByInfoId(id);
        insertPeriods(id, validated.periods());
    }

    @Transactional
    public void delete(Long id) {
        requireTravelInfo(travelInfoMapper.findByIdForUpdate(id));
        if (travelInfoMapper.deleteTravelInfo(id) != 1) {
            throw notFound();
        }
    }

    private ValidatedTravelInfo validate(TravelInfoForm form) {
        if (form == null) {
            throw new TravelInfoValidationException(null, "여행정보를 입력해 주세요.");
        }

        String title = form.getTitle() == null ? "" : form.getTitle().strip();
        form.setTitle(title);
        if (title.isEmpty()) {
            throw new TravelInfoValidationException("title", "제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new TravelInfoValidationException("title", "제목은 255자 이하로 입력해 주세요.");
        }
        if (form.getScope() == null) {
            throw new TravelInfoValidationException("scope", "국내/해외 범위를 선택해 주세요.");
        }
        if (form.getContentType() == null) {
            throw new TravelInfoValidationException("contentType", "여행정보 유형을 선택해 주세요.");
        }

        String content = postContentSanitizer.sanitize(form.getContent());
        form.setContent(content);
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(content);
        boolean hasText = !document.text().strip().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        if (!hasText && !hasImage) {
            throw new TravelInfoValidationException("content", "본문을 입력해 주세요.");
        }

        if (form.getCategoryId() == null) {
            throw new TravelInfoValidationException("categoryId", "정보 카테고리를 선택해 주세요.");
        }
        InfoCategory category = infoCategoryMapper.findById(form.getCategoryId());
        if (category == null) {
            throw new TravelInfoValidationException("categoryId", "존재하지 않는 정보 카테고리입니다.");
        }

        List<ValidatedPeriod> periods = validatePeriods(form);
        return new ValidatedTravelInfo(title, content, periods);
    }

    private List<ValidatedPeriod> validatePeriods(TravelInfoForm form) {
        if (form.getContentType() == TravelInfoContentType.GENERAL) {
            return List.of();
        }

        List<ValidatedPeriod> periods = new ArrayList<>();
        List<InfoPeriodForm> requestedPeriods = form.getPeriods() == null ? List.of() : form.getPeriods();
        for (InfoPeriodForm period : requestedPeriods) {
            if (period == null || (period.getStartDate() == null && period.getEndDate() == null)) {
                continue;
            }
            if (period.getStartDate() == null || period.getEndDate() == null) {
                throw new TravelInfoValidationException("periods", "축제 기간의 시작일과 종료일을 모두 입력해 주세요.");
            }
            if (period.getStartDate().isAfter(period.getEndDate())) {
                throw new TravelInfoValidationException("periods", "축제 기간의 시작일은 종료일보다 늦을 수 없습니다.");
            }
            periods.add(new ValidatedPeriod(period.getStartDate(), period.getEndDate()));
        }

        if (periods.isEmpty()) {
            throw new TravelInfoValidationException("periods", "축제 여행정보는 기간을 한 개 이상 입력해 주세요.");
        }

        Set<ValidatedPeriod> uniquePeriods = new HashSet<>();
        for (ValidatedPeriod period : periods) {
            if (!uniquePeriods.add(period)) {
                throw new TravelInfoValidationException("periods", "동일한 축제 기간을 중복해서 입력할 수 없습니다.");
            }
        }

        periods.sort(Comparator.comparing(ValidatedPeriod::startDate)
                .thenComparing(ValidatedPeriod::endDate));
        for (int index = 1; index < periods.size(); index++) {
            ValidatedPeriod previous = periods.get(index - 1);
            ValidatedPeriod current = periods.get(index);
            if (!current.startDate().isAfter(previous.endDate())) {
                throw new TravelInfoValidationException("periods", "서로 겹치는 축제 기간을 입력할 수 없습니다.");
            }
        }
        return periods;
    }

    private void insertPeriods(Long infoId, List<ValidatedPeriod> periods) {
        for (ValidatedPeriod validatedPeriod : periods) {
            InfoPeriod period = new InfoPeriod();
            period.setInfoId(infoId);
            period.setStartDate(validatedPeriod.startDate());
            period.setEndDate(validatedPeriod.endDate());
            if (travelInfoMapper.insertPeriod(period) != 1) {
                throw new IllegalStateException("여행정보 기간 저장에 실패했습니다.");
            }
        }
    }

    private TravelInfo requireTravelInfo(TravelInfo travelInfo) {
        if (travelInfo == null) {
            throw notFound();
        }
        return travelInfo;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행정보를 찾을 수 없습니다.");
    }

    private record ValidatedTravelInfo(String title, String content, List<ValidatedPeriod> periods) {
    }

    private record ValidatedPeriod(LocalDate startDate, LocalDate endDate) {
    }
}
