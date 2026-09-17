package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryManagementServiceTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-09-18T02:30:00Z");

    @Mock private DiaryCoverLibraryItemMapper itemMapper;

    @Test
    void ownerCanWithdrawAPublishedItemWithoutChangingItsHistoryOrCount() {
        DiaryCoverLibraryItem item = item(DiaryCoverLibraryItemStatus.PUBLISHED, 7L);
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(item);
        when(itemMapper.withdrawByOwner(
                11L, 7L, Timestamp.from(CHANGED_AT))).thenReturn(1);

        service().withdraw(7L, 11L);

        assertThat(item.getDownloadCount()).isEqualTo(23L);
        assertThat(item.getPublishedAt())
                .isEqualTo(Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
        verify(itemMapper).findByIdForUpdate(11L);
        verify(itemMapper).withdrawByOwner(11L, 7L, Timestamp.from(CHANGED_AT));
        verifyNoMoreInteractions(itemMapper);
    }

    @Test
    void ownerCanRepublishAWithdrawnItemWithoutPassingANewPublishedAt() {
        DiaryCoverLibraryItem item = item(DiaryCoverLibraryItemStatus.WITHDRAWN, 7L);
        Timestamp firstPublishedAt = item.getPublishedAt();
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(item);
        when(itemMapper.republishByOwner(11L, 7L)).thenReturn(1);

        service().republish(7L, 11L);

        assertThat(item.getPublishedAt()).isEqualTo(firstPublishedAt);
        assertThat(item.getDownloadCount()).isEqualTo(23L);
        verify(itemMapper).findByIdForUpdate(11L);
        verify(itemMapper).republishByOwner(11L, 7L);
        verifyNoMoreInteractions(itemMapper);
    }

    @ParameterizedTest
    @EnumSource(value = DiaryCoverLibraryItemStatus.class,
            names = {"PUBLISHED", "WITHDRAWN"})
    void ownerCanSoftDeletePublishedOrWithdrawnItems(
            DiaryCoverLibraryItemStatus status) {
        DiaryCoverLibraryItem item = item(status, 7L);
        when(itemMapper.findByIdForUpdate(11L)).thenReturn(item);
        when(itemMapper.softDeleteByOwner(
                11L, 7L, status.name(), Timestamp.from(CHANGED_AT))).thenReturn(1);

        service().delete(7L, 11L);

        assertThat(item.getDownloadCount()).isEqualTo(23L);
        verify(itemMapper).softDeleteByOwner(
                11L, 7L, status.name(), Timestamp.from(CHANGED_AT));
    }

    @Test
    void anotherMemberCannotChangeTheItem() {
        when(itemMapper.findByIdForUpdate(11L))
                .thenReturn(item(DiaryCoverLibraryItemStatus.PUBLISHED, 8L));

        assertThatThrownBy(() -> service().withdraw(7L, 11L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(itemMapper, never()).withdrawByOwner(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(value = DiaryCoverLibraryManagementAction.class,
            names = {"WITHDRAW", "REPUBLISH"})
    void deletedItemCannotBeWithdrawnOrRepublished(
            DiaryCoverLibraryManagementAction action) {
        when(itemMapper.findByIdForUpdate(11L))
                .thenReturn(item(DiaryCoverLibraryItemStatus.DELETED, 7L));

        assertThatThrownBy(() -> action.apply(service(), 7L, 11L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @ParameterizedTest
    @EnumSource(DiaryCoverLibraryManagementAction.class)
    void blockedItemRejectsEveryAuthorAction(
            DiaryCoverLibraryManagementAction action) {
        when(itemMapper.findByIdForUpdate(11L))
                .thenReturn(item(DiaryCoverLibraryItemStatus.BLOCKED, 7L));

        assertThatThrownBy(() -> action.apply(service(), 7L, 11L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private DiaryCoverLibraryManagementServiceImpl service() {
        return new DiaryCoverLibraryManagementServiceImpl(
                itemMapper, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));
    }

    private DiaryCoverLibraryItem item(
            DiaryCoverLibraryItemStatus status, Long creatorUserId) {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(11L);
        item.setCreatorUserId(creatorUserId);
        item.setStatus(status);
        item.setDownloadCount(23L);
        item.setPublishedAt(Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
        return item;
    }

    private enum DiaryCoverLibraryManagementAction {
        WITHDRAW {
            @Override
            void apply(DiaryCoverLibraryManagementService service, Long userId, Long itemId) {
                service.withdraw(userId, itemId);
            }
        },
        REPUBLISH {
            @Override
            void apply(DiaryCoverLibraryManagementService service, Long userId, Long itemId) {
                service.republish(userId, itemId);
            }
        },
        DELETE {
            @Override
            void apply(DiaryCoverLibraryManagementService service, Long userId, Long itemId) {
                service.delete(userId, itemId);
            }
        };

        abstract void apply(
                DiaryCoverLibraryManagementService service, Long userId, Long itemId);
    }
}
