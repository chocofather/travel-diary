package com.example.travlediary.service.diary;

public interface DiaryCoverLibraryManagementService {

    void withdraw(Long userId, Long libraryItemId);

    void republish(Long userId, Long libraryItemId);

    void delete(Long userId, Long libraryItemId);
}
