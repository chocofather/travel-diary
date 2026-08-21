package com.example.travlediary.dto.kto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * detailIntro2 응답 파싱 계약.
 * 실제 KorService2 응답 필드명을 그대로 받아 값을 손실 없이 보존한다.
 * (숫자/boolean 변환은 이 단계에서 하지 않는다)
 */
class KtoTourApiResponseIntroParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesRestaurantIntroFieldsAsPlainStrings() throws Exception {
        String json = """
                {"contentid":"2891928","contenttypeid":"39",
                 "seat":"","kidsfacility":"0","firstmenu":"밀면",
                 "treatmenu":"돼지국밥 / 비빔면 / 섞어국밥 등","smoking":"","packing":"불가능",
                 "infocenterfood":"031-915-1459","scalefood":"중형","parkingfood":"가능",
                 "opendatefood":"","opentimefood":"11:00~22:00","restdatefood":"연중무휴",
                 "discountinfofood":"","chkcreditcardfood":"모든 카드 사용 가능",
                 "reservationfood":"","lcnsno":"20070327326"}
                """;

        KtoTourApiResponse.Item item = objectMapper.readValue(json, KtoTourApiResponse.Item.class);

        assertThat(item.firstmenu()).isEqualTo("밀면");
        assertThat(item.opentimefood()).isEqualTo("11:00~22:00");
        assertThat(item.restdatefood()).isEqualTo("연중무휴");
        assertThat(item.parkingfood()).isEqualTo("가능");
        assertThat(item.packing()).isEqualTo("불가능");
        assertThat(item.infocenterfood()).isEqualTo("031-915-1459");
        // 실제 응답 필드명은 소문자 scalefood 다
        assertThat(item.scalefood()).isEqualTo("중형");
        assertThat(item.treatmenu()).isEqualTo("돼지국밥 / 비빔면 / 섞어국밥 등");
        assertThat(item.chkcreditcardfood()).isEqualTo("모든 카드 사용 가능");
        assertThat(item.kidsfacility()).isEqualTo("0");
        assertThat(item.lcnsno()).isEqualTo("20070327326");
        assertThat(item.seat()).isEmpty();
        assertThat(item.reservationfood()).isEmpty();
        assertThat(item.smoking()).isEmpty();
        assertThat(item.discountinfofood()).isEmpty();
        assertThat(item.opendatefood()).isEmpty();
    }

    @Test
    void parsesAccommodationIntroFieldsAsPlainStrings() throws Exception {
        String json = """
                {"contentid":"2650614","contenttypeid":"32",
                 "roomcount":"192실",
                 "roomtype":"크리스탈 더블 / 크리스탈 트윈 / 사파이어 더블 / 사파이어 디럭스 / 사파이어 패밀리 더블 / 사파이어 트리플 / 스위트",
                 "refundregulation":"소비자분쟁해결기준 중 숙박업 환불 기준에 따름",
                 "checkintime":"15:00","checkouttime":"11:00","chkcooking":"가능",
                 "seminar":"0","sports":"0","sauna":"0","beauty":"0","beverage":"0","karaoke":"0",
                 "barbecue":"0","campfire":"0","bicycle":"0","fitness":"0","publicpc":"0",
                 "publicbath":"0",
                 "subfacility":"비즈니스 센터 / 피트니스 센터 / 미팅룸 / 하늘정원",
                 "foodplace":"Queen's Table","reservationurl":"","pickup":"불가능",
                 "infocenterlodging":"02-580-7500","parkinglodging":"가능 (객실당 1대 무료 주차)",
                 "reservationlodging":"","scalelodging":"지하 7층~지상 12층",
                 "accomcountlodging":"400명"}
                """;

        KtoTourApiResponse.Item item = objectMapper.readValue(json, KtoTourApiResponse.Item.class);

        assertThat(item.checkintime()).isEqualTo("15:00");
        assertThat(item.checkouttime()).isEqualTo("11:00");
        // 숫자로 바꾸지 않고 원문 그대로 보존한다
        assertThat(item.roomcount()).isEqualTo("192실");
        assertThat(item.roomtype()).hasSize(69).startsWith("크리스탈 더블").endsWith("스위트");
        assertThat(item.parkinglodging()).isEqualTo("가능 (객실당 1대 무료 주차)");
        assertThat(item.infocenterlodging()).isEqualTo("02-580-7500");
        assertThat(item.subfacility()).isEqualTo("비즈니스 센터 / 피트니스 센터 / 미팅룸 / 하늘정원");
        assertThat(item.seminar()).isEqualTo("0");
        assertThat(item.sauna()).isEqualTo("0");
        assertThat(item.chkcooking()).isEqualTo("가능");
        assertThat(item.foodplace()).isEqualTo("Queen's Table");
        assertThat(item.pickup()).isEqualTo("불가능");
        assertThat(item.scalelodging()).isEqualTo("지하 7층~지상 12층");
        assertThat(item.accomcountlodging()).isEqualTo("400명");
        assertThat(item.refundregulation()).startsWith("소비자분쟁해결기준");
        assertThat(item.reservationurl()).isEmpty();
        assertThat(item.reservationlodging()).isEmpty();
    }

    @Test
    void keepsExistingContentTypeFieldsUntouched() throws Exception {
        String json = """
                {"contentid":"126508","contenttypeid":"12","restdate":"매주 월요일",
                 "usetime":"09:00~18:00","usefee":"무료","infocenter":"02-762-8261",
                 "expguide":"안내","restdateculture":"c1","usetimeculture":"c2",
                 "infocenterculture":"c3","restdateleports":"l1","usetimeleports":"l2",
                 "usefeeleports":"l3","infocenterleports":"l4","infocentertourcourse":"t1",
                 "schedule":"t2","restdateshopping":"s1","opentime":"s2","infocentershopping":"s3"}
                """;

        KtoTourApiResponse.Item item = objectMapper.readValue(json, KtoTourApiResponse.Item.class);

        assertThat(item.restdate()).isEqualTo("매주 월요일");
        assertThat(item.usetime()).isEqualTo("09:00~18:00");
        assertThat(item.usefee()).isEqualTo("무료");
        assertThat(item.infocenter()).isEqualTo("02-762-8261");
        assertThat(item.expguide()).isEqualTo("안내");
        assertThat(List.of(item.restdateculture(), item.usetimeculture(), item.infocenterculture()))
                .containsExactly("c1", "c2", "c3");
        assertThat(List.of(item.restdateleports(), item.usetimeleports(),
                item.usefeeleports(), item.infocenterleports()))
                .containsExactly("l1", "l2", "l3", "l4");
        assertThat(List.of(item.infocentertourcourse(), item.schedule()))
                .containsExactly("t1", "t2");
        assertThat(List.of(item.restdateshopping(), item.opentime(), item.infocentershopping()))
                .containsExactly("s1", "s2", "s3");
    }

    @Test
    void unknownFieldsAreStillIgnored() throws Exception {
        String json = """
                {"contentid":"1","contenttypeid":"32","benikia":"0","goodstay":"1","hanok":"0"}
                """;

        KtoTourApiResponse.Item item = objectMapper.readValue(json, KtoTourApiResponse.Item.class);

        assertThat(item.contentid()).isEqualTo("1");
        assertThat(item.contenttypeid()).isEqualTo("32");
    }
}
