package com.example.travlediary.repository;

import com.example.travlediary.model.User;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyUsernameRemovalContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.user.UserMapper";

    @Test
    void userDomainAndMapperDoNotDependOnLegacyUsernameColumn() throws IOException {
        assertThat(Arrays.stream(User.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("username");

        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/UserMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(mapper).doesNotContain("username");
    }

    @Test
    void userMapperParsesAndKeepsAutomaticUserResultMapping() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        assertThat(configuration.getAutoMappingUnknownColumnBehavior())
                .isEqualTo(AutoMappingUnknownColumnBehavior.NONE);
        try (InputStream input = Resources.getResourceAsStream("mapper/UserMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/UserMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.getMappedStatement(NAMESPACE + ".findByEmail")
                .getResultMaps())
                .singleElement()
                .extracting(resultMap -> resultMap.getType())
                .isEqualTo(User.class);
        assertThat(configuration.getMappedStatement(NAMESPACE + ".finalizeWithdrawal")
                .getBoundSql(new User()).getSql())
                .contains("SET user_email = ?")
                .doesNotContain("SET ,", ", ,");
    }
}
