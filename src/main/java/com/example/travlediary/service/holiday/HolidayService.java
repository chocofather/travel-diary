package com.example.travlediary.service.holiday;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한국천문연구원 특일 정보. 달력에 쓰는 공휴일 / 24절기 / 잡절만 읽는다.
 * 세 가지 모두 같은 서비스라 요청 방식·XML 파싱·월 단위 캐시를 그대로 함께 쓴다.
 *
 * 인증키는 holiday.api-key 설정값(환경변수)만 쓰고 코드/로그 어디에도 남기지 않는다.
 */
@Slf4j
@Service
public class HolidayService {

    private static final String BASE_URL =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/";
    /** 공휴일 조회 */
    private static final String REST_DAY = "getRestDeInfo";
    /** 24절기 조회 */
    private static final String DIVISIONS = "get24DivisionsInfo";
    /** 잡절 조회 (초복·중복·말복 등) */
    private static final String SUNDRY_DAY = "getSundryDayInfo";
    /** 한 달 특일은 많아야 몇 건이라 한 번에 다 받는다. */
    private static final int NUM_OF_ROWS = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** locdate 형식 (예: 20260815) */
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 오래 켜 둬도 메모리가 늘지 않도록 담아 두는 달 수를 제한한다. */
    private static final int MAX_CACHED_MONTHS = 60;

    /**
     * 화면에 적을 이름만 바꾼다. (응답 원문이나 공휴일 판정은 그대로 둔다)
     * 달력에서 낯설게 읽히는 이름만 여기에 한 줄씩 더하면 된다.
     */
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "기독탄신일", "크리스마스",
            // 응답에 오는 이름은 띄어쓰기 없는 '1월1일' 이다.
            "1월1일", "새해");

    /**
     * 공휴일도 절기도 아니지만 달력에 적어 두면 좋은 날.
     * 해마다 같은 날짜라 외부 API 없이 여기에서만 정한다. (공휴일로 치지 않는다)
     */
    private static final List<LocalDay> LOCAL_DAYS = List.of(
            new LocalDay(MonthDay.of(5, 8), "어버이날"),
            new LocalDay(MonthDay.of(12, 24), "크리스마스 이브"));

    /** 달력이 스스로 아는 표시일. (날짜 + 이름, 종류는 언제나 LOCAL_DAY) */
    private record LocalDay(MonthDay date, String name) {
    }

    /** 달력을 열 때마다 다시 부르지 않도록 월 단위로 담아 둔다. (서버를 내리면 사라진다) */
    private final Map<YearMonth, Map<LocalDate, SpecialDays>> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final String apiKey;

    public HolidayService(@Value("${holiday.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 그 달의 특일 정보(공휴일 + 24절기 + 잡절 + 달력 자체 표시일).
     * 날짜 하나에 여러 건이 있을 수 있어 날짜별 목록으로 담는다.
     * 인증키가 없거나 외부 API 가 실패하면 그만큼만 비고, 달력은 그대로 그려진다.
     */
    public Map<LocalDate, SpecialDays> findSpecialDays(YearMonth month) {
        if (month == null) {
            return Map.of();
        }

        Map<LocalDate, SpecialDays> cached = cache.get(month);
        if (cached != null) {
            return cached;
        }

        // 세 가지를 따로 부른다. 하나가 실패해도 나머지는 그대로 보여 준다.
        boolean asked = !apiKey.isEmpty();
        List<Dated> holidays = asked ? fetch(month, REST_DAY, SpecialDay.Kind.HOLIDAY) : null;
        List<Dated> terms = asked ? fetch(month, DIVISIONS, SpecialDay.Kind.SEASONAL_TERM) : null;
        List<Dated> sundries = asked ? fetch(month, SUNDRY_DAY, SpecialDay.Kind.SUNDRY_DAY) : null;

        Map<LocalDate, List<SpecialDay>> byDate = new LinkedHashMap<>();
        // 공휴일 → 절기 → 잡절 → 달력 표시일 순으로 담아 화면에서도 같은 순서로 적힌다.
        collect(byDate, holidays);
        collect(byDate, terms);
        collect(byDate, sundries);
        collect(byDate, localDays(month));

        Map<LocalDate, SpecialDays> specialDays = new LinkedHashMap<>();
        byDate.forEach((date, days) -> specialDays.put(date, new SpecialDays(List.copyOf(days))));
        Map<LocalDate, SpecialDays> result = Map.copyOf(specialDays);

        // 부른 것이 모두 성공했을 때만 담아 둔다. (실패한 달은 다음에 다시 부른다)
        if (!asked || (holidays != null && terms != null && sundries != null)) {
            if (cache.size() >= MAX_CACHED_MONTHS) {
                cache.clear();
            }
            cache.put(month, result);
        }
        return result;
    }

    /** 그 달에 해당하는 달력 표시일. 해마다 같은 날짜라 외부에 물어볼 것이 없다. */
    private List<Dated> localDays(YearMonth month) {
        return LOCAL_DAYS.stream()
                .filter(day -> day.date().getMonthValue() == month.getMonthValue()
                        && day.date().getDayOfMonth() <= month.lengthOfMonth())
                .map(day -> new Dated(month.atDay(day.date().getDayOfMonth()),
                        new SpecialDay(day.name(), SpecialDay.Kind.LOCAL_DAY)))
                .toList();
    }

    /** 한 종류를 읽는다. 실패하면 null 을 돌려줘 '못 읽음'과 '없음'을 구분한다. */
    private List<Dated> fetch(YearMonth month, String operation, SpecialDay.Kind kind) {
        try {
            // 응답을 String 으로 받으면 charset 이 없는 응답이 ISO-8859-1 로 읽혀 한글이 깨진다.
            // 원본 바이트를 그대로 받아 XML 선언(encoding)을 파서가 직접 해석하게 한다.
            byte[] xml = restClient.get()
                    .uri(requestUri(operation, month))
                    .retrieve()
                    .body(byte[].class);
            return parse(xml, kind);
        } catch (Exception exception) {
            // 인증키가 섞일 수 있는 주소/메시지는 남기지 않는다.
            log.warn("특일 정보를 불러오지 못했습니다. (월: {}, 종류: {}, 원인: {})",
                    month, kind, exception.getClass().getSimpleName());
            return null;
        }
    }

    private void collect(Map<LocalDate, List<SpecialDay>> byDate, List<Dated> days) {
        if (days == null) {
            return;
        }
        for (Dated day : days) {
            byDate.computeIfAbsent(day.date(), key -> new ArrayList<>()).add(day.value());
        }
    }

    /**
     * 응답 XML 에서 날짜/이름만 뽑는다.
     * 공휴일 조회만 isHoliday=Y 로 거르고, 절기·잡절은 응답 항목을 그대로 쓴다.
     *
     * 바이트를 그대로 받아 XML 선언의 인코딩대로 읽는다.
     * (플랫폼 기본 charset 이나 응답 헤더의 기본값에 기대지 않는다)
     */
    List<Dated> parse(byte[] xml, SpecialDay.Kind kind) throws Exception {
        if (xml == null || xml.length == 0) {
            return List.of();
        }

        NodeList items = secureDocumentBuilder()
                .parse(new InputSource(new ByteArrayInputStream(xml)))
                .getElementsByTagName("item");

        List<Dated> found = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            // 실제 쉬는 날만 빨간 날로 쓴다. (절기·잡절 응답에는 이 조건을 걸지 않는다)
            if (kind == SpecialDay.Kind.HOLIDAY
                    && !"Y".equalsIgnoreCase(text(item, "isHoliday"))) {
                continue;
            }

            LocalDate date = date(text(item, "locdate"));
            String name = text(item, "dateName");
            if (date == null || name.isEmpty()) {
                continue;
            }
            // 같은 날짜에 여러 건이 와도 하나도 버리지 않는다. (이름만 읽기 쉬운 쪽으로 바꿔 둔다)
            found.add(new Dated(date, new SpecialDay(displayName(name), kind)));
        }
        return List.copyOf(found);
    }

    /** 달력에 적을 이름. 정해 둔 것이 없으면 응답에 온 이름을 그대로 쓴다. */
    private String displayName(String name) {
        return DISPLAY_NAMES.getOrDefault(name, name);
    }

    /** 파싱 결과를 날짜와 함께 옮기기 위한 값. (화면으로 나가지 않는다) */
    record Dated(LocalDate date, SpecialDay value) {
    }

    /** 외부 XML 이므로 외부 엔티티 참조는 막아 둔다. */
    private javax.xml.parsers.DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private String text(Element item, String tagName) {
        NodeList found = item.getElementsByTagName(tagName);
        return found.getLength() == 0 || found.item(0).getTextContent() == null
                ? "" : found.item(0).getTextContent().strip();
    }

    private LocalDate date(String locdate) {
        try {
            return LocalDate.parse(locdate, LOCDATE);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 요청 주소. 인증키는 포털이 주는 형태(인코딩/디코딩)가 갈리므로
     * 이미 인코딩된 키(% 포함)는 그대로 두고, 아니면 한 번만 인코딩한다.
     */
    private URI requestUri(String operation, YearMonth month) {
        String serviceKey = apiKey.contains("%")
                ? apiKey
                : URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(BASE_URL + operation
                + "?serviceKey=" + serviceKey
                + "&solYear=" + month.getYear()
                + "&solMonth=" + String.format("%02d", month.getMonthValue())
                + "&numOfRows=" + NUM_OF_ROWS
                + "&pageNo=1");
    }
}
