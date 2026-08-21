package com.example.travlediary.dto.kto;

import java.util.List;

public record KtoTourRegionMatchResponse(
        boolean matched,
        List<RegionPathItem> path,
        Long deepestRegionId
) {
    public static KtoTourRegionMatchResponse matched(List<RegionPathItem> path) {
        List<RegionPathItem> immutablePath = List.copyOf(path);
        return new KtoTourRegionMatchResponse(
                true,
                immutablePath,
                immutablePath.get(immutablePath.size() - 1).id()
        );
    }

    public static KtoTourRegionMatchResponse unmatched() {
        return new KtoTourRegionMatchResponse(false, List.of(), null);
    }

    public record RegionPathItem(Long id, String regionName) {
    }
}
