package com.tripbora.service.destinationimport;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.destinationimport.DestinationImportIssue;
import com.tripbora.dto.destinationimport.DestinationImportItem;
import com.tripbora.dto.destinationimport.DestinationImportPreview;
import com.tripbora.dto.destinationimport.DestinationImportResult;
import com.tripbora.dto.destinationimport.DestinationImportRowStatus;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateQuery;
import com.tripbora.service.destination.DestinationDuplicateReason;
import com.tripbora.service.destination.DestinationDuplicateService;
import com.tripbora.service.destination.DestinationDuplicateStatus;
import com.tripbora.service.destination.DestinationNameNormalizer;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.destination.DuplicateDestinationException;
import com.tripbora.service.destination.DuplicateTourApiDestinationException;
import com.tripbora.service.destination.DuplicateWikidataDestinationException;
import com.tripbora.service.kto.KtoTourApiException;
import com.tripbora.service.kto.KtoTourService;
import com.tripbora.service.wikidata.ExternalApiRateLimiter;
import com.tripbora.service.wikidata.WikidataApiException;
import com.tripbora.service.wikidata.WikipediaApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 여행지 JSON 일괄등록.
 *
 * <pre>
 * JSON → 엄격 Parser → Master Resolver(이름 → ID) → Validator → DestinationImportFormMapper → DestinationForm
 *      → DestinationDuplicateService(파일 안·기존 DB) → 미리보기
 * 등록 요청(한 건) → 같은 단계를 처음부터 다시 → DestinationSaveOrchestrationService.registerImportedDestination
 *      (저장 직전 공통 중복 판별 → 기존 Persistence / Service / Mapper)
 * </pre>
 * 미리보기 결과는 서버에 남기지 않는다. 등록 요청은 화면이 보낸 JSON 항목을 다시 검증하고, 미리보기를 믿지 않는다.
 * 여행지마다 따로 저장하므로 한 건의 실패가 다른 건을 되돌리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationImportService {

    private final DestinationImportParser parser;
    private final DestinationImportMasterResolver resolver;
    private final DestinationImportValidator validator;
    private final DestinationImportFormMapper formMapper;
    private final DestinationDuplicateService duplicateService;
    private final DestinationSaveOrchestrationService orchestrationService;
    private final DestinationService destinationService;
    private final KtoTourService ktoTourService;

    /** 한 건을 검증·매핑한 중간 결과. */
    private static final class Evaluation {
        private final int index;
        private final String path;
        private final DestinationImportItem item;
        private final List<DestinationImportIssue> errors = new ArrayList<>();
        private final List<DestinationImportIssue> warnings = new ArrayList<>();
        private List<String> factFields = List.of();
        private DestinationImportMasterResolver.Resolution resolution;
        private String ktoContentId;
        private String wikidataQid;
        private DestinationForm form;
        private DestinationDuplicateQuery query;
        private DestinationDuplicateCheck duplicate;
        private DestinationImportPreview.FileDuplicate fileDuplicate;

        private Evaluation(int index, String path, DestinationImportItem item) {
            this.index = index;
            this.path = path;
            this.item = item;
        }

        private String key() {
            return item == null ? null : item.key();
        }

        private String sourceType() {
            if (ktoContentId != null) return DestinationService.KTO_TOUR_API_SOURCE_TYPE;
            if (wikidataQid != null) return DestinationService.WIKIDATA_SOURCE_TYPE;
            return DestinationService.ADMIN_SOURCE_TYPE;
        }

        private String externalId() {
            return ktoContentId != null ? ktoContentId : wikidataQid;
        }

        private DestinationImportRowStatus status() {
            if (!errors.isEmpty()) return DestinationImportRowStatus.INVALID;
            if (duplicate != null && duplicate.confirmed()) return DestinationImportRowStatus.REGISTERED;
            if ((duplicate != null && duplicate.needsReview()) || fileDuplicate != null) {
                return DestinationImportRowStatus.POSSIBLE_DUPLICATE;
            }
            return DestinationImportRowStatus.NOT_REGISTERED;
        }
    }

    /** TourAPI 확인 결과. title 이 empty 면 그 contentId 가 없다. failure 가 있으면 확인하지 못했다. */
    private record TourApiCheck(Optional<String> title, String failure) {
    }

    public DestinationImportPreview preview(String json) {
        DestinationImportParser.ParsedFile file = parser.parseFile(json);
        if (!file.errors().isEmpty()) {
            return DestinationImportPreview.failed(file.errors());
        }
        DestinationImportMasterResolver.MasterData master = resolver.load();
        List<Evaluation> evaluations = file.items().stream().map(item -> evaluate(item, master)).toList();
        validator.duplicateKeys(file.items())
                .forEach((position, issue) -> evaluations.get(position).errors.add(issue));

        markFileDuplicates(evaluations);

        // 기존 여행지와의 판별: 저장 직전과 같은 질의(DestinationDuplicateQuery.fromForm)로 한 번에 본다.
        List<Evaluation> checkable = evaluations.stream().filter(evaluation -> evaluation.errors.isEmpty()).toList();
        List<DestinationDuplicateCheck> checks = duplicateService.checkAll(
                checkable.stream().map(evaluation -> evaluation.query).toList());
        for (int position = 0; position < checkable.size(); position++) {
            checkable.get(position).duplicate = checks.get(position);
        }

        // TourAPI contentId 는 실제로 있는지 확인한다. 같은 contentId 는 한 번만 묻고,
        // 이미 그 contentId 로 등록된 여행지는 존재가 확인된 것이라 묻지 않는다.
        Map<String, TourApiCheck> tourApi = new HashMap<>();
        for (Evaluation evaluation : evaluations) {
            if (evaluation.ktoContentId == null || registeredByExternalId(evaluation.duplicate)
                    || (evaluation.fileDuplicate != null && evaluation.fileDuplicate.confirmed())) {
                continue;
            }
            TourApiCheck check = tourApi.computeIfAbsent(evaluation.ktoContentId, this::lookupTourApi);
            applyTourApi(evaluation, check);
        }
        return DestinationImportPreview.of(evaluations.stream().map(this::row).toList());
    }

    /**
     * 여행지 한 건을 등록한다. 요청 본문의 JSON 항목을 처음부터 다시 파싱·검증·매핑하고,
     * 저장 직전에 공통 중복 판별을 다시 한다. 실패는 결과로 돌려준다.
     */
    public DestinationImportResult register(String body, Long userId) {
        DestinationImportParser.ParsedRegisterRequest request = parser.parseRegisterRequest(body);
        if (!request.errors().isEmpty()) {
            return DestinationImportResult.invalid(request.index(), null, request.errors());
        }
        Evaluation evaluation = evaluate(request.item(), resolver.load());
        int index = request.index();
        String key = evaluation.key();
        if (!evaluation.errors.isEmpty()) {
            return DestinationImportResult.invalid(index, key, evaluation.errors);
        }
        if (evaluation.ktoContentId != null) {
            applyTourApi(evaluation, lookupTourApi(evaluation.ktoContentId));
            if (!evaluation.errors.isEmpty()) {
                return DestinationImportResult.invalid(index, key, evaluation.errors);
            }
        }
        DestinationForm form = evaluation.form;
        form.setAllowPossibleDuplicate(request.allowPossibleDuplicate());
        try {
            Long destinationId = orchestrationService.registerImportedDestination(form, userId);
            return DestinationImportResult.success(index, key, destinationId);
        } catch (DuplicateDestinationException exception) {
            DestinationDuplicateCheck check = exception.getCheck();
            return check.confirmed()
                    ? DestinationImportResult.registered(index, key, check)
                    : DestinationImportResult.possibleDuplicate(index, key, check);
        } catch (DuplicateWikidataDestinationException | DuplicateTourApiDestinationException exception) {
            return registeredByExternalId(index, key, evaluation);
        } catch (DuplicateKeyException exception) {
            if (evaluation.externalId() != null) {
                return registeredByExternalId(index, key, evaluation);
            }
            log.warn("JSON 일괄등록 저장 실패 (index={}, key={}, 원인=중복 키)", index, key);
            return DestinationImportResult.failed(index, key, "여행지를 저장하지 못했습니다. 다시 시도해 주세요.");
        } catch (IllegalArgumentException | NoSuchElementException | WikidataApiException
                 | WikipediaApiException exception) {
            return DestinationImportResult.failed(index, key, exception.getMessage());
        } catch (DataAccessException exception) {
            log.warn("JSON 일괄등록 DB 저장 실패 (index={}, key={})", index, key, exception);
            return DestinationImportResult.failed(index, key, "여행지를 저장하지 못했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.");
        } catch (RuntimeException exception) {
            if (ExternalApiRateLimiter.rateLimitOf(exception).isPresent()) {
                return DestinationImportResult.failed(index, key,
                        "외부 API 요청 제한에 걸렸습니다. 잠시 후 실패한 여행지를 다시 등록해 주세요.");
            }
            log.warn("JSON 일괄등록 실패 (index={}, key={})", index, key, exception);
            return DestinationImportResult.failed(index, key, "여행지를 저장하지 못했습니다. 다시 시도해 주세요.");
        }
    }

    private Evaluation evaluate(DestinationImportParser.ParsedItem parsed,
                                DestinationImportMasterResolver.MasterData master) {
        Evaluation evaluation = new Evaluation(parsed.index(), parsed.path(), parsed.item());
        evaluation.errors.addAll(parsed.errors());
        DestinationImportItem item = parsed.item();
        if (item == null) {
            return evaluation;
        }
        DestinationImportMasterResolver.Resolution resolution = resolver.resolve(item, parsed.path(), master);
        evaluation.resolution = resolution;
        evaluation.errors.addAll(resolution.errors());
        DestinationImportValidator.Result validation = validator.validate(item, parsed.path(), resolution.domestic());
        evaluation.errors.addAll(validation.errors());
        evaluation.warnings.addAll(validation.warnings());
        evaluation.factFields = validation.factFields();

        DestinationImportItem.External external = item.external();
        if (Boolean.TRUE.equals(resolution.domestic())
                && DestinationImportValidator.isTourApiContentId(external.tourApiContentId())) {
            evaluation.ktoContentId = external.tourApiContentId();
        }
        if (Boolean.FALSE.equals(resolution.domestic())) {
            evaluation.wikidataQid = DestinationImportValidator.normalizeQid(external.wikidataQid());
        }
        if (evaluation.errors.isEmpty()) {
            evaluation.form = formMapper.toForm(item, resolution, evaluation.ktoContentId, evaluation.wikidataQid);
            // 저장 직전 판별(DestinationSaveOrchestrationService)과 같은 질의를 쓴다.
            evaluation.query = DestinationDuplicateQuery.fromForm(evaluation.form,
                    evaluation.externalId() == null ? null : evaluation.sourceType(), evaluation.externalId());
        } else {
            evaluation.query = partialQuery(evaluation, item, resolution);
        }
        return evaluation;
    }

    /** 오류가 있는 행도 파일 안 중복을 볼 수 있게, 읽을 수 있는 값만으로 질의를 만든다. */
    private DestinationDuplicateQuery partialQuery(Evaluation evaluation, DestinationImportItem item,
                                                   DestinationImportMasterResolver.Resolution resolution) {
        List<String> names = DestinationImportFields.LANGUAGES.stream()
                .map(item::translation).filter(Objects::nonNull)
                .map(DestinationImportItem.Text::name).filter(Objects::nonNull).toList();
        boolean coordinates = item.latitude() != null && item.longitude() != null
                && item.latitude().abs().doubleValue() <= 90 && item.longitude().abs().doubleValue() <= 180;
        String placeId = DestinationImportValidator.isGooglePlaceId(item.external().googlePlaceId())
                ? item.external().googlePlaceId() : null;
        return new DestinationDuplicateQuery(evaluation.externalId() == null ? null : evaluation.sourceType(),
                evaluation.externalId(), placeId, names, resolution.regionId(),
                coordinates ? item.latitude() : null, coordinates ? item.longitude() : null);
    }

    /**
     * 같은 파일 안의 중복. 기존 여행지와 같은 규칙(DestinationDuplicateService)으로 앞 행과만 비교한다.
     * 같은 외부 ID·Place ID 면 뒤 행을 오류로, 이름·위치만 같으면 중복 확인으로 표시한다.
     */
    private void markFileDuplicates(List<Evaluation> evaluations) {
        List<DestinationDuplicateCheck> checks = duplicateService.checkWithinBatch(
                evaluations.stream().map(evaluation -> evaluation.query).toList());
        for (int position = 0; position < evaluations.size(); position++) {
            DestinationDuplicateCheck check = checks.get(position);
            if (check.status() == DestinationDuplicateStatus.NOT_REGISTERED || check.destinationId() == null) {
                continue;
            }
            Evaluation evaluation = evaluations.get(position);
            Evaluation earlier = evaluations.get(check.destinationId().intValue());
            String label = "#" + (earlier.index + 1) + (earlier.key() == null ? "" : " (" + earlier.key() + ")")
                    + (check.destinationName() == null ? "" : " " + check.destinationName());
            if (check.confirmed()) {
                evaluation.errors.add(new DestinationImportIssue(evaluation.path + ".external"
                        + (check.reason() == DestinationDuplicateReason.GOOGLE_PLACE_ID ? ".googlePlaceId"
                        : evaluation.ktoContentId != null ? ".tourApiContentId" : ".wikidataQid"),
                        "같은 파일의 " + label + "과 같은 값입니다(" + check.message() + "). 한 건만 남겨 주세요."));
            }
            evaluation.fileDuplicate = new DestinationImportPreview.FileDuplicate(earlier.index, earlier.key(),
                    check.destinationName(), check.confirmed(), check.confirmed()
                    ? "같은 파일의 " + label + "과 같은 여행지입니다(" + check.message() + ")."
                    : "같은 파일의 " + label + "과 같은 곳일 수 있습니다(" + check.message() + ").");
        }
    }

    private TourApiCheck lookupTourApi(String contentId) {
        try {
            return new TourApiCheck(ktoTourService.findTitle(contentId), null);
        } catch (KtoTourApiException exception) {
            return new TourApiCheck(Optional.empty(), exception.getMessage());
        }
    }

    private void applyTourApi(Evaluation evaluation, TourApiCheck check) {
        String path = evaluation.path + ".external.tourApiContentId";
        if (check.failure() != null) {
            evaluation.errors.add(new DestinationImportIssue(path, "TourAPI 에서 contentId 를 확인하지 못했습니다("
                    + check.failure() + "). 잠시 후 다시 미리보기 하거나 contentId 를 비워 주세요."));
            return;
        }
        if (check.title().isEmpty()) {
            evaluation.errors.add(new DestinationImportIssue(path,
                    "TourAPI 에 없는 contentId 입니다: " + evaluation.ktoContentId));
            return;
        }
        String title = check.title().get();
        String koreanName = evaluation.item.koreanName();
        String expected = DestinationNameNormalizer.normalize(koreanName);
        if (expected == null || !expected.equals(DestinationNameNormalizer.normalize(title))) {
            evaluation.warnings.add(new DestinationImportIssue(path, "TourAPI 이름 '" + title + "'과 한국어 이름 '"
                    + koreanName + "'이 다릅니다. 같은 여행지의 contentId 인지 확인해 주세요."));
        }
    }

    private static boolean registeredByExternalId(DestinationDuplicateCheck check) {
        return check != null && check.confirmed() && check.reason() == DestinationDuplicateReason.EXTERNAL_CONTENT_ID;
    }

    private DestinationImportResult registeredByExternalId(int index, String key, Evaluation evaluation) {
        Long existing = evaluation.ktoContentId != null
                ? destinationService.findTourApiDestinationId(evaluation.ktoContentId)
                : evaluation.wikidataQid != null ? destinationService.findWikidataDestinationId(evaluation.wikidataQid)
                : null;
        return DestinationImportResult.registered(index, key, new DestinationDuplicateCheck(
                DestinationDuplicateStatus.REGISTERED, DestinationDuplicateReason.EXTERNAL_CONTENT_ID, existing,
                null, null, DestinationDuplicateReason.EXTERNAL_CONTENT_ID.label()));
    }

    private DestinationImportPreview.Row row(Evaluation evaluation) {
        DestinationImportItem item = evaluation.item;
        DestinationImportMasterResolver.Resolution resolution = evaluation.resolution;
        String regionLabel = resolution != null && resolution.regionId() != null ? resolution.regionLabel()
                : item == null ? null : rawRegion(item.region());
        return new DestinationImportPreview.Row(
                evaluation.index,
                evaluation.path,
                evaluation.key(),
                item == null ? null : item.koreanName(),
                regionLabel,
                item == null || item.type() == null ? null : item.type().name(),
                item == null || item.season() == null ? null : item.season().name(),
                item == null ? List.of() : item.categories(),
                resolution == null || resolution.mainCategoryName() == null
                        ? (item == null ? null : item.mainCategory()) : resolution.mainCategoryName(),
                evaluation.sourceType(),
                evaluation.externalId(),
                evaluation.status(),
                evaluation.duplicate,
                evaluation.fileDuplicate,
                List.copyOf(evaluation.errors),
                List.copyOf(evaluation.warnings),
                item == null ? List.of() : item.evidence(),
                evaluation.factFields);
    }

    private static String rawRegion(DestinationImportItem.Region region) {
        List<String> parts = new ArrayList<>();
        if (region.country() != null) parts.add(region.country());
        if (region.city() != null) parts.add(region.city());
        if (region.district() != null) parts.add(region.district());
        return parts.isEmpty() ? null : String.join(" > ", parts);
    }
}
