package com.example.travlediary.repository;

import com.example.travlediary.model.DestinationImage;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationImageMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.destination.DestinationMapper";

    @Test
    void destinationImageExposesSourceMetadataProperties() throws IntrospectionException {
        Map<String, Class<?>> propertyTypes = Arrays.stream(
                        Introspector.getBeanInfo(DestinationImage.class).getPropertyDescriptors())
                .collect(Collectors.toMap(PropertyDescriptor::getName,
                        PropertyDescriptor::getPropertyType));

        assertThat(propertyTypes)
                .containsEntry("sourceType", String.class)
                .containsEntry("sourceName", String.class)
                .containsEntry("externalContentId", String.class)
                .containsEntry("sourceTitle", String.class)
                .containsEntry("photographer", String.class)
                .containsEntry("licenseType", String.class)
                .containsEntry("sourceImageUrl", String.class)
                .containsEntry("licenseCheckedAt", Timestamp.class);
    }

    @Test
    void imageQueriesMapAllSourceMetadataColumns() throws IOException {
        Configuration configuration = mapperConfiguration();
        ResultMap imageMap = configuration.getResultMap(NAMESPACE + ".imageMap");
        Map<String, String> columnsByProperty = imageMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(),
                        mapping -> mapping.getColumn()));

        assertThat(columnsByProperty)
                .containsEntry("sourceType", "source_type")
                .containsEntry("sourceName", "source_name")
                .containsEntry("externalContentId", "external_content_id")
                .containsEntry("sourceTitle", "source_title")
                .containsEntry("photographer", "photographer")
                .containsEntry("licenseType", "license_type")
                .containsEntry("sourceImageUrl", "source_image_url")
                .containsEntry("licenseCheckedAt", "license_checked_at");

        for (String statementId : List.of("findDestinationDetail", "findImagesByDestinationId")) {
            String sql = normalizedSql(configuration, statementId, 10L);
            assertThat(sql)
                    .contains("source_type")
                    .contains("source_name")
                    .contains("external_content_id")
                    .contains("source_title")
                    .contains("photographer")
                    .contains("license_type")
                    .contains("source_image_url")
                    .contains("license_checked_at");
        }
    }

    @Test
    void insertStoresSourceMetadataAndDefaultsMissingSourceTypeToAdminUpload() throws IOException {
        Configuration configuration = mapperConfiguration();
        DestinationImage image = new DestinationImage();
        image.setImageUrl("/uploads/destinations/admin.jpg");
        image.setSourceType(null);

        BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insertImage")
                .getBoundSql(image);
        String sql = normalizedSql(configuration, "insertImage", image);

        assertThat(sql)
                .contains("image_url, source_type, source_name, external_content_id, source_title, "
                        + "photographer, license_type, source_image_url, license_checked_at")
                .contains("VALUES (?, COALESCE(?, 'ADMIN_UPLOAD'), ?, ?, ?, ?, ?, ?, ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly(
                        "imageUrl",
                        "sourceType",
                        "sourceName",
                        "externalContentId",
                        "sourceTitle",
                        "photographer",
                        "licenseType",
                        "sourceImageUrl",
                        "licenseCheckedAt",
                        "isMain",
                        "isSlide",
                        "orderIndex",
                        "destinationId",
                        "createdAt"
                );
    }

    @Test
    void slideStateCanBeUpdatedForOneImage() throws IOException {
        Configuration configuration = mapperConfiguration();

        String sql = normalizedSql(configuration, "updateImageSlide", Map.of(
                "imageId", 2L,
                "isSlide", true
        ));

        assertThat(sql)
                .isEqualTo("UPDATE destination_images SET is_slide = ? WHERE id = ?");
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/DestinationMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/DestinationMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalizedSql(Configuration configuration, String statementId, Object parameter) {
        return configuration.getMappedStatement(NAMESPACE + "." + statementId)
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
    }
}
