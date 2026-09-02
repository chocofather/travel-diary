package com.example.travlediary.repository;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAccountMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.user.SocialAccountMapper";

    @Test
    void providerIdentityLookupBindsProviderAndProviderUserIdOnly() throws IOException {
        Configuration configuration = mapperConfiguration();
        BoundSql boundSql = configuration.getMappedStatement(
                        NAMESPACE + ".findByProviderAndProviderUserId")
                .getBoundSql(Map.of(
                        "provider", SocialProvider.GOOGLE,
                        "providerUserId", "google-123"));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, user_id, provider, provider_user_id, provider_email, "
                        + "provider_email_verified, created_at, updated_at "
                        + "FROM social_accounts WHERE provider = ? AND provider_user_id = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("provider", "providerUserId");
    }

    @Test
    void userProviderAndProviderListQueriesUseTheDeclaredIdentityBoundaries()
            throws IOException {
        Configuration configuration = mapperConfiguration();

        BoundSql userProvider = configuration.getMappedStatement(
                        NAMESPACE + ".findByUserIdAndProvider")
                .getBoundSql(Map.of("userId", 10L, "provider", SocialProvider.KAKAO));
        BoundSql providerList = configuration.getMappedStatement(NAMESPACE + ".findAllByUserId")
                .getBoundSql(10L);

        assertThat(normalize(userProvider.getSql())).isEqualTo(
                "SELECT id, user_id, provider, provider_user_id, provider_email, "
                        + "provider_email_verified, created_at, updated_at "
                        + "FROM social_accounts WHERE user_id = ? AND provider = ?");
        assertThat(userProvider.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("userId", "provider");
        assertThat(normalize(providerList.getSql())).isEqualTo(
                "SELECT id, user_id, provider, provider_user_id, provider_email, "
                        + "provider_email_verified, created_at, updated_at "
                        + "FROM social_accounts WHERE user_id = ? ORDER BY created_at ASC, id ASC");
    }

    @Test
    void insertMapsOptionalEmailAndTriStateVerificationWithoutTokens() throws IOException {
        Configuration configuration = mapperConfiguration();
        SocialAccount account = new SocialAccount();
        account.setUserId(10L);
        account.setProvider(SocialProvider.NAVER);
        account.setProviderUserId("naver-123");
        account.setProviderEmail(null);
        account.setProviderEmailVerified(null);

        BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insert")
                .getBoundSql(account);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "INSERT INTO social_accounts (user_id, provider, provider_user_id, provider_email, "
                        + "provider_email_verified) VALUES (?, ?, ?, ?, ?)");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("userId", "provider", "providerUserId", "providerEmail",
                        "providerEmailVerified");
    }

    @Test
    void resultMapRepresentsNullableEmailVerificationAndAuditColumns() throws IOException {
        ResultMap resultMap = mapperConfiguration().getResultMap(NAMESPACE + ".SocialAccountResultMap");
        Map<String, String> columns = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(), mapping -> mapping.getColumn()));

        assertThat(columns)
                .containsEntry("id", "id")
                .containsEntry("userId", "user_id")
                .containsEntry("provider", "provider")
                .containsEntry("providerUserId", "provider_user_id")
                .containsEntry("providerEmail", "provider_email")
                .containsEntry("providerEmailVerified", "provider_email_verified")
                .containsEntry("createdAt", "created_at")
                .containsEntry("updatedAt", "updated_at");
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/SocialAccountMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/SocialAccountMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
    }
}
