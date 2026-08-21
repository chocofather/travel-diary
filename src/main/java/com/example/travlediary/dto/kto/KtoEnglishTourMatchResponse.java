package com.example.travlediary.dto.kto;

import java.util.List;

public record KtoEnglishTourMatchResponse(
        Status status,
        KtoEnglishTourCandidateResponse matched,
        List<KtoEnglishTourCandidateResponse> candidates
) {
    public enum Status {
        MATCHED,
        CANDIDATES,
        NO_MATCH
    }

    public static KtoEnglishTourMatchResponse matched(KtoEnglishTourCandidateResponse candidate) {
        return new KtoEnglishTourMatchResponse(Status.MATCHED, candidate, List.of());
    }

    public static KtoEnglishTourMatchResponse candidates(List<KtoEnglishTourCandidateResponse> candidates) {
        return new KtoEnglishTourMatchResponse(Status.CANDIDATES, null, List.copyOf(candidates));
    }

    public static KtoEnglishTourMatchResponse noMatch() {
        return new KtoEnglishTourMatchResponse(Status.NO_MATCH, null, List.of());
    }
}
