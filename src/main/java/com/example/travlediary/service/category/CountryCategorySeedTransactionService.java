package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 국가·지역 기준 데이터의 저장 경계. 빠진 행을 한 트랜잭션에서 모두 넣는다.
 *
 * <p>지역 트리는 여행지·코스·검색이 함께 쓰는 기준 데이터다. 중간에 실패해 절반만 들어가면
 * "일부 지역이 목록에 없다" 는 형태로만 드러나 원인을 찾기 어렵다. 그래서 전부 들어가거나
 * 하나도 들어가지 않거나 둘 중 하나가 되게 한다.
 *
 * <p>{@link com.example.travlediary.config.CountryCategoryLoader} 가 아니라 별도 빈으로
 * 둔 이유는 {@code @PostConstruct} 때문이다. 같은 빈 안에서 부르면 Spring 프록시를 지나지
 * 않아 {@code @Transactional} 이 아무 일도 하지 않는다. 다른 빈으로 주입받아 부르면 주입된
 * 참조가 곧 프록시라 트랜잭션이 실제로 걸린다.
 *
 * <p>기존 행은 건드리지 않는다. JSON 은 "없는 행을 채우는" 자료일 뿐이라, 운영에서 고친
 * 이름·아이콘·노출 여부를 기동할 때마다 되돌리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CountryCategorySeedTransactionService {

    private final CountryCategoryMapper mapper;
    /** 기준 데이터를 채우고 나면 그 전에 들고 있던 값은 버린다. */
    private final CountryCategoryCache cache;

    /**
     * 아직 없는 지역만 넣는다.
     *
     * @param categories 평탄화한 지역 목록 (트리 순서 그대로여야 부모가 먼저 들어간다)
     * @return 이번에 새로 넣은 행 수
     */
    @Transactional
    public int insertMissing(List<CountryCategory> categories) {
        Set<Integer> existingIds = new HashSet<>(mapper.selectAllIds());

        int inserted = 0;
        for (CountryCategory category : categories) {
            Long id = category.getId();
            // id 가 없는 항목은 기존 행과 맞춰 볼 수 없으므로 -1 로 두어 새 행으로 본다.
            int comparableId = id != null ? id.intValue() : -1;
            if (existingIds.contains(comparableId)) {
                continue;
            }
            mapper.insert(category);
            inserted++;
        }

        /*
          넣은 줄이 없더라도 버린다. 비어 있는 캐시는 늘 옳지만, 남아 있는 캐시는 틀릴 수 있다.
          이 트랜잭션이 되돌아가도 마찬가지다 — 다시 읽을 뿐이라 잘못된 값이 남지 않는다.
          기동 중에만 일어나는 일이라 다시 읽는 비용도 문제가 되지 않는다.
        */
        cache.invalidate();
        return inserted;
    }
}
