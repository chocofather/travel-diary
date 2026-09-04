package com.example.travlediary.dto.kto;

import java.util.List;

public record KtoForeignTourMatchResponse(
        Status status,
        KtoForeignTourCandidateResponse matched,
        List<KtoForeignTourCandidateResponse> candidates
) {
    public enum Status {
        MATCHED,
        CANDIDATES,
        NO_MATCH
    }

    public static KtoForeignTourMatchResponse matched(KtoForeignTourCandidateResponse candidate) {
        return new KtoForeignTourMatchResponse(Status.MATCHED, candidate, List.of());
    }

    public static KtoForeignTourMatchResponse candidates(List<KtoForeignTourCandidateResponse> candidates) {
        return new KtoForeignTourMatchResponse(Status.CANDIDATES, null, List.copyOf(candidates));
    }

    public static KtoForeignTourMatchResponse noMatch() {
        return new KtoForeignTourMatchResponse(Status.NO_MATCH, null, List.of());
    }
}
