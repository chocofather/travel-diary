package com.tripbora.service.travelinfo.structured;

import java.util.List;

/**
 * 화면 렌더링 테스트용 경복궁·창덕궁 표본. 1차 블록 6종과, 한 페이지의 슬라이더 여러 개(6장 · 3장 · 1장)를 담는다.
 * DB 에 넣지 않고 Service 가 넘겨줄 typed model 을 그대로 만든다.
 */
public final class StructuredContentSamples {

    private StructuredContentSamples() {
    }

    public static StructuredImage image(int number, int width, int height) {
        return new StructuredImage(String.format(
                "/uploads/travel-info/content/%08d-1111-4222-8333-444444444444.jpg", number), width, height);
    }

    /** 한국어 원문 표본. */
    public static StructuredContent palaceTour() {
        return new StructuredContent(1, List.of(
                new StructuredBlock.SectionTitle("gyeongbok-intro", "경복궁"),
                new StructuredBlock.RichText("gyeongbok-text",
                        "조선 왕조의 법궁인 경복궁은 1395년에 지어졌습니다.\n광화문에서 시작해 북쪽으로 걸어 들어갑니다."
                                + "\n\n두 번째 문단입니다. <script>alert('x')</script> 는 글자 그대로 보여야 합니다."),
                new StructuredBlock.FullImage("gyeongbok-overview", image(1, 2000, 1333),
                        "북악산 아래 펼쳐진 경복궁 전경", "북악산 아래 펼쳐진 경복궁"),
                new StructuredBlock.ImageText("geunjeongjeon", StructuredBlock.ImagePosition.LEFT,
                        image(2, 1600, 1200), "근정전 정면", "근정전",
                        "왕의 즉위식과 큰 의례가 열리던 경복궁의 정전입니다.\n\n품계석이 늘어선 마당을 함께 보세요."),
                new StructuredBlock.ImageSlider("gyeongbok-halls", "경복궁 주요 전각", List.of(
                        item("gwanghwamun", 3, "광화문", "경복궁의 정문"),
                        item("heungnyemun", 4, "흥례문", "광화문 다음에 지나는 두 번째 문"),
                        item("geunjeongjeon-slide", 5, "근정전", null),
                        item("sajeongjeon", 6, "사정전", "왕이 신하들과 나랏일을 의논하던 편전"),
                        item("donggung", 7, "동궁", "세자가 머물던 공간"),
                        item("gyeonghoeru", 8, "경회루",
                                "연못 위에 지은 누각으로, 외국 사신을 맞거나 연회를 열던 곳입니다. "
                                        + "해 질 무렵 연못에 비친 모습이 특히 아름답습니다."))),
                new StructuredBlock.Callout("gyeongbok-closed", "경복궁은 매주 화요일 휴궁합니다."),
                new StructuredBlock.SectionTitle("changdeok-intro", "창덕궁"),
                new StructuredBlock.ImageText("injeongjeon", StructuredBlock.ImagePosition.RIGHT,
                        image(9, 1200, 1500), "인정전 처마", "인정전",
                        "창덕궁의 정전으로, 자연 지형을 살린 배치가 돋보입니다."),
                new StructuredBlock.ImageSlider("changdeok-garden", "후원 산책", List.of(
                        item("buyongji", 10, "부용지", "후원의 대표 연못"),
                        item("juhamnu", 11, "주합루", null),
                        item("aeryeonji", 12, null, null))),
                new StructuredBlock.ImageSlider("deoksu-single", null, List.of(
                        item("seokjojeon", 13, "석조전", "덕수궁의 서양식 건물"))),
                new StructuredBlock.ImageGrid("changdeok-pair", 2, List.of(
                        item("nakseonjae", 14, "낙선재", "단청을 칠하지 않은 소박한 건물"),
                        item("buyongjeong", 15, "부용정", null))),
                new StructuredBlock.ImageGrid("deoksu-trio", 3, List.of(
                        item("daehanmun", 16, "대한문", "덕수궁의 정문"),
                        // alt·제목 없이 설명만 있는 세로 사진. (alt 는 설명으로 대신 채운다)
                        new StructuredBlock.SliderItem("junghwajeon", image(17, 1200, 1600), null, null, "중화전 처마"),
                        item("stonewall", 18, null, "돌담길")))));
    }

    /**
     * Service 가 영어 번역을 덮어써 넘겨준 결과 표본. (이미지·순서는 원문 그대로)
     * 섹션 제목 lead 는 예전 번역에 남아 있을 수 있는 값이다. 읽기만 하고 버려진다.
     */
    public static StructuredContent palaceTourInEnglish() {
        StructuredContent korean = palaceTour();
        return StructuredContentTestSupport.structuredContentService().localize(korean, """
                {"blocks": {
                  "gyeongbok-intro": {"title": "Gyeongbokgung", "lead": "The main royal palace of Joseon"},
                  "gyeongbok-overview": {"alt": "Gyeongbokgung below Bugaksan", "caption": "Gyeongbokgung at a glance"},
                  "gyeongbok-halls": {"title": "Main halls of Gyeongbokgung", "items": {
                    "gwanghwamun": {"title": "Gwanghwamun", "caption": "The main gate", "alt": "Gwanghwamun gate"}
                  }},
                  "gyeongbok-closed": {"text": "Gyeongbokgung is closed on Tuesdays."}
                }}""");
    }

    private static StructuredBlock.SliderItem item(String id, int imageNumber, String title, String caption) {
        String alt = (title == null ? "후원" : title) + " 사진";
        return new StructuredBlock.SliderItem(id, image(imageNumber, 1800, 1200), alt, title, caption);
    }
}
