package com.tripbora.service.destinationimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 파일 안 key 중복: 뒤 행만 오류, key 가 없는 행끼리는 중복이 아니다. */
class DestinationImportValidatorTest {

    private final DestinationImportParser parser = new DestinationImportParser();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aRepeatedKeyMakesOnlyTheLaterRowsInvalidAndRowsWithoutAKeyNeverCollide() throws Exception {
        List<DestinationImportParser.ParsedItem> items = new ArrayList<>();
        String[] rows = {"{\"key\": \"gyeongbokgung\"}", "{}", "{\"key\": \"fukuoka-tower\"}",
                "{\"key\": \" gyeongbokgung \"}", "{\"key\": \"\"}", "{\"key\": \"gyeongbokgung\"}"};
        for (int index = 0; index < rows.length; index++) {
            items.add(parser.parseItem(json.readTree(rows[index]), index));
        }

        var duplicates = new DestinationImportValidator().duplicateKeys(items);

        assertThat(duplicates).containsOnlyKeys(3, 5);
        assertThat(duplicates.get(3).text()).isEqualTo("destinations[3].key: 같은 key 'gyeongbokgung'가 "
                + "destinations[0]에도 있습니다. key 는 파일 안에서 한 번만 쓸 수 있습니다.");
        assertThat(duplicates.get(5).text()).startsWith("destinations[5].key: 같은 key 'gyeongbokgung'가 destinations[0]");
    }
}
