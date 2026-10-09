package com.tripbora.service.category;

import com.tripbora.config.i18n.SupportedLanguage;
import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.CountryCategoryTranslation;
import com.tripbora.repository.category.CountryCategoryMapper;
import com.tripbora.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 관리자 지역 등록·삭제.
 *
 * <p>계층은 "최상위(대륙·대한민국) → 국가·시/도 → 도시·시/군/구" 세 단계다. 공개 여행지 목록과
 * 여행지 등록폼이 이 세 단계만 다루므로, 관리자도 최상위 바로 아래와 그 아래까지만 등록한다.
 * 최상위 자체는 등록하지 않는다.
 *
 * <p>depth 값은 입력받지 않는다. 국내 시/도처럼 depth 가 부모보다 하나 큰 값이 아닌 경우가 있어
 * 같은 부모 아래(없으면 같은 계층의 다른 지역)가 쓰는 값을 그대로 따른다.
 *
 * <p>지역 기준 데이터는 {@link CountryCategoryCache} 가 들고 있다. 바꾼 뒤에는 반드시 버린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryCategoryAdminService {

    /** 최상위 → 2단계 → leaf. 공개 지역 네비게이션과 여행지 등록폼이 다루는 계층 수. */
    static final int MAX_LEVELS = 3;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int CODE_MAX_LENGTH = 20;
    /** 해외 국가 코드. 코스 국가 목록이 '-' 없는 코드를 국가로 본다. */
    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2,3}$");

    private static final String KO = SupportedLanguage.KOREAN.getLanguageTag();
    private static final String EN = SupportedLanguage.ENGLISH.getLanguageTag();
    private static final String JA = SupportedLanguage.JAPANESE.getLanguageTag();
    private static final String ZH_CN = SupportedLanguage.CHINESE_SIMPLIFIED.getLanguageTag();
    private static final String ZH_TW = SupportedLanguage.CHINESE_TRADITIONAL.getLanguageTag();

    private final CountryCategoryMapper mapper;
    private final CountryCategoryService countryCategoryService;
    private final CountryCategorySeedTransactionService seedService;
    private final FileUploadService fileUploadService;
    private final CountryCategoryCache cache;

    /** 등록 화면의 부모 선택지. 최상위마다 한 묶음이고, 최상위 자신과 그 바로 아래 지역을 담는다. */
    public record ParentOptionGroup(String label, boolean domestic, List<ParentOption> options) {
    }

    /**
     * @param codeRequired 이 부모 아래에 등록하면 code 가 필요한지 (최상위 바로 아래)
     * @param codePrefix   국내 시/도 코드의 앞머리 (예: "KR-"). 해외 국가는 null
     */
    public record ParentOption(Long id, String label, boolean codeRequired, String codePrefix) {
    }

    public List<ParentOptionGroup> getParentOptionGroups() {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        List<ParentOptionGroup> groups = new ArrayList<>();
        for (Long rootId : countryCategoryService.getDomesticRootIds()) {
            CountryCategory root = countryCategoryService.getById(rootId);
            if (root != null) {
                groups.add(optionGroup(root, true, collator));
            }
        }
        for (CountryCategory root : countryCategoryService.getOverseasContinentRegions()) {
            groups.add(optionGroup(root, false, collator));
        }
        return groups;
    }

    private ParentOptionGroup optionGroup(CountryCategory root, boolean domestic, Collator collator) {
        List<ParentOption> options = new ArrayList<>();
        options.add(new ParentOption(root.getId(),
                root.getRegionName() + (domestic ? " → 시/도" : " → 국가"),
                true, domestic ? codePrefix(root) : null));
        mapper.selectByParentId(root.getId()).stream()
                .filter(CountryCategoryAdminService::visible)
                .sorted(Comparator.comparing(CountryCategory::getRegionName,
                        Comparator.nullsLast(collator)))
                .forEach(child -> options.add(new ParentOption(child.getId(),
                        child.getRegionName() + (domestic ? " → 시/군/구" : " → 도시/지역"),
                        false, null)));
        return new ParentOptionGroup(root.getRegionName(), domestic, List.copyOf(options));
    }

    /**
     * 지역 하나와 그 번역을 한 트랜잭션으로 등록한다.
     *
     * @return 저장한 지역 (생성된 id 포함)
     * @throws CountryCategoryValidationException 부모·이름·코드·중복 검증 실패. 아무것도 저장하지 않는다.
     */
    @Transactional
    public CountryCategory create(CountryCategoryForm form) {
        if (form == null || form.getParentId() == null) {
            throw new CountryCategoryValidationException("parentId", "부모 지역을 선택해 주세요.");
        }
        List<CountryCategory> path = countryCategoryService.getRegionPath(form.getParentId());
        if (path.isEmpty()) {
            throw new CountryCategoryValidationException("parentId", "선택한 부모 지역을 찾을 수 없습니다.");
        }
        CountryCategory root = path.get(0);
        CountryCategory parent = path.get(path.size() - 1);
        boolean domestic = countryCategoryService.getDomesticRootIds().contains(root.getId());
        boolean overseas = countryCategoryService.getOverseasRootIds().contains(root.getId());
        if ((!domestic && !overseas) || path.size() >= MAX_LEVELS) {
            throw new CountryCategoryValidationException("parentId", "이 지역 아래에는 하위 지역을 등록할 수 없습니다.");
        }

        String regionName = requiredName(form.getRegionName(), "regionName", "한국어명");
        String nameEn = requiredName(form.getNameEn(), "nameEn", "영어명");
        Map<String, String> names = new LinkedHashMap<>();
        names.put(KO, regionName);
        names.put(EN, nameEn);
        names.put(JA, optionalName(form.getNameJa(), "nameJa", "일본어명"));
        names.put(ZH_CN, optionalName(form.getNameZhCn(), "nameZhCn", "중국어 간체명"));
        names.put(ZH_TW, optionalName(form.getNameZhTw(), "nameZhTw", "중국어 번체명"));

        // 최상위 바로 아래(국가·시/도)만 코드가 필요하다. 그 아래 leaf 는 임의 코드를 만들지 않고 비워 둔다.
        boolean codeRequired = path.size() == 1;
        String code = codeRequired ? requiredCode(form.getCode(), root, domestic) : null;

        ensureNoDuplicateSibling(parent.getId(), names);

        CountryCategory region = new CountryCategory();
        region.setRegionName(regionName);
        region.setNameEn(nameEn);
        region.setCode(code);
        region.setParentId(parent.getId());
        region.setDepth(resolveDepth(parent));
        region.setIsVisible(1);
        mapper.insertRegion(region);

        names.forEach((languageCode, name) -> {
            if (name == null) {
                return;
            }
            CountryCategoryTranslation translation = new CountryCategoryTranslation();
            translation.setCountryCategoryId(region.getId());
            translation.setLanguageCode(languageCode);
            translation.setName(name);
            mapper.insertTranslation(translation);
        });

        invalidateCache();
        return region;
    }

    /**
     * 아무 데도 쓰이지 않는 지역만 지운다.
     *
     * <p>번역과 지역 태그 매핑은 FK ON DELETE CASCADE 로 함께 지워지므로 따로 지우지 않는다.
     * 여행지(destinations.region_id)와 코스(courses.country_id)는 FK 가 삭제를 막으므로 먼저 세어
     * 이유를 알려 준다.
     *
     * @return 지운 지역 (목록으로 돌아갈 위치를 정하는 데 쓴다)
     * @throws CountryCategoryDeleteBlockedException 지울 수 없는 경우. 아무것도 지우지 않는다.
     */
    @Transactional
    public CountryCategory delete(Long id) {
        CountryCategory region = id == null ? null : mapper.selectById(id);
        if (region == null) {
            throw new CountryCategoryDeleteBlockedException("삭제할 지역을 찾을 수 없습니다.");
        }
        if (region.getParentId() == null) {
            throw new CountryCategoryDeleteBlockedException("최상위 지역은 삭제할 수 없습니다.");
        }
        if (mapper.countChildren(id) > 0) {
            throw new CountryCategoryDeleteBlockedException("하위 지역이 존재하여 삭제할 수 없습니다.");
        }
        if (mapper.countDestinationsByRegionId(id) > 0) {
            throw new CountryCategoryDeleteBlockedException("이 지역을 사용하는 여행지가 있어 삭제할 수 없습니다.");
        }
        if (mapper.countCoursesByCountryId(id) > 0) {
            throw new CountryCategoryDeleteBlockedException("이 지역을 사용하는 여행 코스가 있어 삭제할 수 없습니다.");
        }
        if (seedService.isSeedRegion(id)) {
            // 지워도 다음 기동 때 기준 데이터(JSON)가 같은 번호로 다시 채운다.
            throw new CountryCategoryDeleteBlockedException(
                    "기본 제공 지역은 삭제할 수 없습니다. 재시작하면 기준 데이터로 다시 생성됩니다.");
        }

        String iconPath = region.getIconPath();
        boolean iconShared = iconPath != null && mapper.countOtherRegionsByIconPath(id, iconPath) > 0;
        try {
            if (mapper.deleteById(id) != 1) {
                throw new CountryCategoryDeleteBlockedException("삭제할 지역을 찾을 수 없습니다.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new CountryCategoryDeleteBlockedException(
                    "다른 데이터가 이 지역을 참조하고 있어 삭제할 수 없습니다.", exception);
        }

        if (iconPath != null && !iconShared) {
            deleteIconAfterCommit(iconPath);
        }
        invalidateCache();
        return region;
    }

    private String requiredName(String value, String field, String label) {
        String name = optionalName(value, field, label);
        if (name == null) {
            throw new CountryCategoryValidationException(field, label + "을 입력해 주세요.");
        }
        return name;
    }

    private String optionalName(String value, String field, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.strip();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CountryCategoryValidationException(field,
                    label + "은 " + NAME_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        return name;
    }

    private String requiredCode(String value, CountryCategory root, boolean domestic) {
        if (value == null || value.isBlank()) {
            throw new CountryCategoryValidationException("code", "코드를 입력해 주세요.");
        }
        String code = value.strip().toUpperCase(Locale.ROOT);
        if (code.length() > CODE_MAX_LENGTH) {
            throw new CountryCategoryValidationException("code", "코드는 " + CODE_MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (domestic) {
            String prefix = codePrefix(root);
            Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "[A-Z0-9]{1,10}$");
            if (!pattern.matcher(code).matches()) {
                throw new CountryCategoryValidationException("code",
                        "코드는 " + prefix + "11 처럼 " + prefix + " 뒤에 영문/숫자로 입력해 주세요.");
            }
        } else if (!COUNTRY_CODE.matcher(code).matches()) {
            throw new CountryCategoryValidationException("code", "국가 코드는 JP 처럼 영문 대문자 2~3자로 입력해 주세요.");
        }
        if (mapper.countByCode(code) > 0) {
            throw new CountryCategoryValidationException("code", "이미 사용 중인 코드입니다: " + code);
        }
        return code;
    }

    private static String codePrefix(CountryCategory root) {
        return (root.getCode() == null ? "" : root.getCode().strip().toUpperCase(Locale.ROOT)) + "-";
    }

    /**
     * 같은 부모 아래에서 같은 언어의 이름이 이미 쓰이면 막는다.
     * (한국어명은 region_name 과 ko 번역, 영어명은 name_en 과 en 번역까지 함께 본다)
     */
    private void ensureNoDuplicateSibling(Long parentId, Map<String, String> names) {
        List<CountryCategory> siblings = mapper.selectByParentId(parentId);
        if (siblings == null || siblings.isEmpty()) {
            return;
        }
        Map<String, Set<String>> taken = new HashMap<>();
        for (CountryCategory sibling : siblings) {
            addTaken(taken, KO, sibling.getRegionName());
            addTaken(taken, EN, sibling.getNameEn());
        }
        List<Long> siblingIds = siblings.stream().map(CountryCategory::getId).filter(Objects::nonNull).toList();
        List<CountryCategoryTranslation> translations = mapper.findTranslationsByCountryCategoryIds(siblingIds);
        if (translations != null) {
            for (CountryCategoryTranslation translation : translations) {
                addTaken(taken, translation.getLanguageCode(), translation.getName());
            }
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String name = entry.getValue();
            if (name != null && taken.getOrDefault(entry.getKey(), Set.of()).contains(comparable(name))) {
                throw new CountryCategoryValidationException(fieldOf(entry.getKey()),
                        "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: " + name);
            }
        }
    }

    private static void addTaken(Map<String, Set<String>> taken, String languageCode, String name) {
        if (languageCode == null || name == null || name.isBlank()) {
            return;
        }
        taken.computeIfAbsent(languageCode, key -> new HashSet<>()).add(comparable(name));
    }

    private static String comparable(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String fieldOf(String languageCode) {
        if (KO.equals(languageCode)) return "regionName";
        if (EN.equals(languageCode)) return "nameEn";
        if (JA.equals(languageCode)) return "nameJa";
        if (ZH_CN.equals(languageCode)) return "nameZhCn";
        return "nameZhTw";
    }

    /**
     * 형제 지역이 쓰는 depth 를 따른다. 형제가 없으면 같은 계층(부모의 형제들의 자식)이 쓰는 값,
     * 그것도 없으면 부모 depth + 1.
     */
    private int resolveDepth(CountryCategory parent) {
        List<Integer> depths = mapper.selectChildDepths(parent.getId());
        if ((depths == null || depths.isEmpty()) && parent.getParentId() != null) {
            depths = mapper.selectGrandchildDepths(parent.getParentId());
        }
        int parentDepth = parent.getDepth() == null ? 0 : parent.getDepth();
        if (depths == null || depths.isEmpty()) {
            return parentDepth + 1;
        }
        if (depths.size() > 1 || depths.get(0) == null || depths.get(0) <= parentDepth) {
            throw new CountryCategoryValidationException("parentId",
                    "선택한 부모 아래 지역의 계층 정보가 일정하지 않아 등록할 수 없습니다.");
        }
        return depths.get(0);
    }

    private static boolean visible(CountryCategory category) {
        return !Integer.valueOf(0).equals(category.getIsVisible());
    }

    /**
     * 지금 바로 버리고, 트랜잭션이 끝난 뒤에도 한 번 더 버린다.
     * 커밋 전 사이에 다른 요청이 옛 값을 다시 담아 두었더라도 커밋 뒤에는 남지 않는다.
     */
    private void invalidateCache() {
        cache.invalidate();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.invalidate();
                }
            });
        }
    }

    /** DB 삭제가 확정된 뒤에만 아이콘 파일을 지운다. 롤백되면 파일은 그대로 둔다. */
    private void deleteIconAfterCommit(String iconPath) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteIconQuietly(iconPath);
                }
            });
            return;
        }
        deleteIconQuietly(iconPath);
    }

    private void deleteIconQuietly(String iconPath) {
        try {
            fileUploadService.deleteSavedFile(iconPath, CountryCategoryService.ICON_DIRECTORY);
        } catch (RuntimeException cleanupFailure) {
            log.warn("삭제한 지역의 아이콘 파일을 정리하지 못했습니다. (원인: {})",
                    cleanupFailure.getClass().getSimpleName());
        }
    }
}
