package com.example.travlediary.service.search;

import com.example.travlediary.dto.GlobalSearchPage;

public interface GlobalSearchService {

    GlobalSearchPage search(String query, String type, int page);
}
