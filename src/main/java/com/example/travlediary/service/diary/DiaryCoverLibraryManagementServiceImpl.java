package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

@Service
public class DiaryCoverLibraryManagementServiceImpl
        implements DiaryCoverLibraryManagementService {

    private final DiaryCoverLibraryItemMapper itemMapper;
    private final Clock clock;

    @Autowired
    public DiaryCoverLibraryManagementServiceImpl(
            DiaryCoverLibraryItemMapper itemMapper) {
        this(itemMapper, Clock.systemUTC());
    }

    DiaryCoverLibraryManagementServiceImpl(
            DiaryCoverLibraryItemMapper itemMapper, Clock clock) {
        this.itemMapper = itemMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void withdraw(Long userId, Long libraryItemId) {
        DiaryCoverLibraryItem item = ownedItemForUpdate(userId, libraryItemId);
        requireStatus(item, DiaryCoverLibraryItemStatus.PUBLISHED);
        int changed = itemMapper.withdrawByOwner(
                item.getId(), userId, Timestamp.from(clock.instant()));
        requireChanged(changed);
    }

    @Override
    @Transactional
    public void republish(Long userId, Long libraryItemId) {
        DiaryCoverLibraryItem item = ownedItemForUpdate(userId, libraryItemId);
        requireStatus(item, DiaryCoverLibraryItemStatus.WITHDRAWN);
        requireChanged(itemMapper.republishByOwner(item.getId(), userId));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long libraryItemId) {
        DiaryCoverLibraryItem item = ownedItemForUpdate(userId, libraryItemId);
        if (item.getStatus() != DiaryCoverLibraryItemStatus.PUBLISHED
                && item.getStatus() != DiaryCoverLibraryItemStatus.WITHDRAWN) {
            throw invalidTransition();
        }
        int changed = itemMapper.softDeleteByOwner(
                item.getId(), userId, item.getStatus().name(),
                Timestamp.from(clock.instant()));
        requireChanged(changed);
    }

    private DiaryCoverLibraryItem ownedItemForUpdate(Long userId, Long libraryItemId) {
        DiaryCoverValues.requireUser(userId);
        if (libraryItemId == null) {
            throw notFound();
        }
        DiaryCoverLibraryItem item = itemMapper.findByIdForUpdate(libraryItemId);
        if (item == null || !Objects.equals(item.getCreatorUserId(), userId)) {
            throw notFound();
        }
        return item;
    }

    private void requireStatus(
            DiaryCoverLibraryItem item, DiaryCoverLibraryItemStatus expected) {
        if (item.getStatus() != expected) {
            throw invalidTransition();
        }
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw invalidTransition();
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "관리할 공유 표지를 찾을 수 없습니다.");
    }

    private ResponseStatusException invalidTransition() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "현재 상태에서는 변경할 수 없습니다.");
    }
}
