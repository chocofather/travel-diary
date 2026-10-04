package com.tripbora.repository.seo;

import com.tripbora.seo.SitemapTravelInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SitemapMapper {

    List<Long> findDestinationIds();

    List<SitemapTravelInfo> findTravelInfoEntries();

    List<Long> findEventIds();

    List<Long> findPostIds();

    List<Long> findCourseIds();
}
