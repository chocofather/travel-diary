package com.example.travlediary.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 공개 상세 페이지의 schema.org JSON-LD를 실제 모델 값으로만 만든다. */
public final class SeoStructuredData {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTEXT = "https://schema.org";

    private SeoStructuredData() {
    }

    public static void website(Model model) {
        String url = absolute(model, "/");
        if (!hasText(url)) {
            return;
        }
        Map<String, Object> website = new LinkedHashMap<>();
        website.put("@context", CONTEXT);
        website.put("@type", "WebSite");
        website.put("name", "TripBora");
        website.put("alternateName", "트립보라");
        website.put("url", url);
        model.addAttribute("seoJsonLd", scriptSafeJson(website));
    }

    public static void article(Model model, String canonicalPath, String headline,
                               String description, String imageUrl,
                               Timestamp publishedAt, Timestamp modifiedAt,
                               String authorName) {
        Map<String, Object> article = entity("Article", headline, description);
        putText(article, "headline", headline);
        putText(article, "image", absolute(model, imageUrl));
        putText(article, "datePublished", isoDateTime(publishedAt));
        putText(article, "dateModified", isoDateTime(modifiedAt));
        if (hasText(authorName)) {
            Map<String, Object> author = new LinkedHashMap<>();
            author.put("@type", "Person");
            author.put("name", authorName.strip());
            article.put("author", author);
        }
        apply(model, canonicalPath, headline, article);
    }

    public static void place(Model model, String canonicalPath, String name,
                             String description, String imageUrl, String address,
                             BigDecimal latitude, BigDecimal longitude) {
        Map<String, Object> place = entity("Place", name, description);
        putText(place, "image", absolute(model, imageUrl));
        putText(place, "address", address);
        if (latitude != null && longitude != null) {
            Map<String, Object> geo = new LinkedHashMap<>();
            geo.put("@type", "GeoCoordinates");
            geo.put("latitude", latitude);
            geo.put("longitude", longitude);
            place.put("geo", geo);
        }
        apply(model, canonicalPath, name, place);
    }

    public static void event(Model model, String canonicalPath, String name,
                             String description, String imageUrl,
                             LocalDate startDate, LocalDate endDate,
                             String placeName, String address) {
        Map<String, Object> event = entity("Event", name, description);
        putText(event, "image", absolute(model, imageUrl));
        putText(event, "startDate", startDate == null ? null : startDate.toString());
        putText(event, "endDate", endDate == null ? null : endDate.toString());
        if (hasText(placeName) || hasText(address)) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("@type", "Place");
            putText(location, "name", placeName);
            if (hasText(address)) {
                Map<String, Object> postalAddress = new LinkedHashMap<>();
                postalAddress.put("@type", "PostalAddress");
                postalAddress.put("streetAddress", address.strip());
                location.put("address", postalAddress);
            }
            event.put("location", location);
        }
        apply(model, canonicalPath, name, event);
    }

    public static void touristTrip(Model model, String canonicalPath, String name,
                                   String description, String imageUrl,
                                   List<ItineraryStop> stops) {
        Map<String, Object> trip = entity("TouristTrip", name, description);
        putText(trip, "image", absolute(model, imageUrl));
        List<Map<String, Object>> items = new ArrayList<>();
        if (stops != null) {
            int position = 1;
            for (ItineraryStop stop : stops) {
                if (stop == null || !hasText(stop.name())) {
                    continue;
                }
                Map<String, Object> place = new LinkedHashMap<>();
                place.put("@type", "Place");
                place.put("name", stop.name().strip());
                if (stop.destinationId() != null) {
                    place.put("url", absolute(model, "/destinations/" + stop.destinationId()));
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("@type", "ListItem");
                item.put("position", position++);
                item.put("item", place);
                items.add(item);
            }
        }
        if (!items.isEmpty()) {
            Map<String, Object> itinerary = new LinkedHashMap<>();
            itinerary.put("@type", "ItemList");
            itinerary.put("itemListElement", items);
            trip.put("itinerary", itinerary);
        }
        apply(model, canonicalPath, name, trip);
    }

    private static Map<String, Object> entity(String type, String name, String description) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("@type", type);
        putText(entity, "name", name);
        putText(entity, "description", SeoTextUtils.summary(description));
        return entity;
    }

    private static void apply(Model model, String canonicalPath, String currentName,
                              Map<String, Object> entity) {
        if (!hasText(currentName) || !hasText(canonicalPath)) {
            return;
        }
        String canonicalUrl = absolute(model, canonicalPath);
        if (!hasText(canonicalUrl)) {
            return;
        }
        entity.put("url", canonicalUrl);
        if ("Article".equals(entity.get("@type"))) {
            entity.put("mainEntityOfPage", canonicalUrl);
        }

        Map<String, Object> breadcrumb = new LinkedHashMap<>();
        breadcrumb.put("@type", "BreadcrumbList");
        breadcrumb.put("itemListElement", List.of(
                breadcrumbItem(1, "TripBora", absolute(model, "/")),
                breadcrumbItem(2, currentName.strip(), canonicalUrl)));

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("@context", CONTEXT);
        graph.put("@graph", List.of(entity, breadcrumb));
        model.addAttribute("seoJsonLd", scriptSafeJson(graph));
    }

    private static Map<String, Object> breadcrumbItem(int position, String name, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("@type", "ListItem");
        item.put("position", position);
        item.put("name", name);
        item.put("item", url);
        return item;
    }

    private static String absolute(Model model, String pathOrUrl) {
        Object baseUrl = model.getAttribute("seoSiteBaseUrl");
        if (!(baseUrl instanceof String value) || !hasText(value)) {
            return null;
        }
        return SeoSiteUrl.absolute(value, pathOrUrl);
    }

    private static String isoDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static void putText(Map<String, Object> values, String name, String value) {
        if (hasText(value)) {
            values.put(name, value.strip());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String scriptSafeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value)
                    .replace("&", "\\u0026")
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("\u2028", "\\u2028")
                    .replace("\u2029", "\\u2029");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON-LD 직렬화에 실패했습니다.", exception);
        }
    }

    public record ItineraryStop(String name, Long destinationId) {
    }
}
