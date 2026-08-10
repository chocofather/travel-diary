package com.example.travlediary.service.category;

import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InfoCategoryServiceTest {

    @Mock
    private InfoCategoryMapper infoCategoryMapper;

    private InfoCategoryService infoCategoryService;

    @BeforeEach
    void setUp() {
        infoCategoryService = new InfoCategoryService(infoCategoryMapper);
    }

    @Test
    void getAllReturnsMapperResult() {
        List<InfoCategory> categories = List.of(category(1L, "계절여행", 1, true));
        when(infoCategoryMapper.findAll()).thenReturn(categories);

        assertThat(infoCategoryService.getAll()).isSameAs(categories);
    }

    @Test
    void getVisibleReturnsOnlyMapperVisibleResult() {
        List<InfoCategory> categories = List.of(category(1L, "계절여행", 1, true));
        when(infoCategoryMapper.findVisible()).thenReturn(categories);

        assertThat(infoCategoryService.getVisible()).isSameAs(categories);
        verify(infoCategoryMapper).findVisible();
    }

    @Test
    void createStripsNameAndSavesAllFields() {
        InfoCategoryForm form = form("  여행준비  ", 3, false);
        when(infoCategoryMapper.countByNameExcludingId("여행준비", null)).thenReturn(0);

        infoCategoryService.create(form);

        ArgumentCaptor<InfoCategory> captor = ArgumentCaptor.forClass(InfoCategory.class);
        verify(infoCategoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("여행준비");
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(3);
        assertThat(captor.getValue().getIsVisible()).isFalse();
        assertThat(form.getName()).isEqualTo("여행준비");
    }

    @Test
    void createRejectsDuplicateNameBeforeInsert() {
        InfoCategoryForm form = form("여행준비", 1, true);
        when(infoCategoryMapper.countByNameExcludingId("여행준비", null)).thenReturn(1);

        assertThatThrownBy(() -> infoCategoryService.create(form))
                .isInstanceOf(DuplicateInfoCategoryNameException.class)
                .hasMessage("이미 사용 중인 카테고리명입니다.");
        verify(infoCategoryMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createConvertsDatabaseDuplicateException() {
        InfoCategoryForm form = form("여행준비", 1, true);
        when(infoCategoryMapper.countByNameExcludingId("여행준비", null)).thenReturn(0);
        when(infoCategoryMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("constraint detail"));

        assertThatThrownBy(() -> infoCategoryService.create(form))
                .isInstanceOf(DuplicateInfoCategoryNameException.class)
                .hasMessage("이미 사용 중인 카테고리명입니다.")
                .hasCauseInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void updateAllowsCurrentCategoryNameAndSavesVisibility() {
        InfoCategory existing = category(7L, "핫플레이스", 2, true);
        when(infoCategoryMapper.findById(7L)).thenReturn(existing);
        when(infoCategoryMapper.countByNameExcludingId("핫플레이스", 7L)).thenReturn(0);

        infoCategoryService.update(7L, form(" 핫플레이스 ", 4, false));

        verify(infoCategoryMapper).countByNameExcludingId("핫플레이스", 7L);
        verify(infoCategoryMapper).update(existing);
        assertThat(existing.getDisplayOrder()).isEqualTo(4);
        assertThat(existing.getIsVisible()).isFalse();
    }

    @Test
    void updateRejectsNameOwnedByAnotherCategory() {
        when(infoCategoryMapper.findById(7L)).thenReturn(category(7L, "핫플레이스", 2, true));
        when(infoCategoryMapper.countByNameExcludingId("여행준비", 7L)).thenReturn(1);

        assertThatThrownBy(() -> infoCategoryService.update(7L, form("여행준비", 2, true)))
                .isInstanceOf(DuplicateInfoCategoryNameException.class);
        verify(infoCategoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdReturnsNotFoundForMissingCategory() {
        when(infoCategoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> infoCategoryService.getById(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateReturnsNotFoundBeforeDuplicateCheckForMissingCategory() {
        when(infoCategoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> infoCategoryService.update(99L, form("여행준비", 1, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(infoCategoryMapper, never()).countByNameExcludingId(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void serviceDefensivelyRejectsInvalidOrder() {
        assertThatThrownBy(() -> infoCategoryService.create(form("여행준비", 0, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("표시 순서는 1 이상이어야 합니다.");
        verify(infoCategoryMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRemovesUnusedCategory() {
        when(infoCategoryMapper.findById(7L)).thenReturn(category(7L, "핫플레이스", 2, true));
        when(infoCategoryMapper.countTravelInfoByCategoryId(7L)).thenReturn(0);
        when(infoCategoryMapper.deleteById(7L)).thenReturn(1);

        infoCategoryService.delete(7L);

        verify(infoCategoryMapper).deleteById(7L);
    }

    @Test
    void deleteRejectsCategoryUsedByTravelInfo() {
        when(infoCategoryMapper.findById(7L)).thenReturn(category(7L, "핫플레이스", 2, true));
        when(infoCategoryMapper.countTravelInfoByCategoryId(7L)).thenReturn(2);

        assertThatThrownBy(() -> infoCategoryService.delete(7L))
                .isInstanceOf(InfoCategoryInUseException.class)
                .hasMessage("이 카테고리를 사용하는 여행정보가 있어 삭제할 수 없습니다.");
        verify(infoCategoryMapper, never()).deleteById(7L);
    }

    @Test
    void deleteConvertsDatabaseForeignKeyException() {
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("foreign key constraint detail");
        when(infoCategoryMapper.findById(7L)).thenReturn(category(7L, "핫플레이스", 2, true));
        when(infoCategoryMapper.countTravelInfoByCategoryId(7L)).thenReturn(0);
        when(infoCategoryMapper.deleteById(7L)).thenThrow(databaseException);

        assertThatThrownBy(() -> infoCategoryService.delete(7L))
                .isInstanceOf(InfoCategoryInUseException.class)
                .hasMessage("이 카테고리를 사용하는 여행정보가 있어 삭제할 수 없습니다.")
                .hasCause(databaseException);
    }

    @Test
    void deleteReturnsNotFoundBeforeUsageCheckForMissingCategory() {
        when(infoCategoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> infoCategoryService.delete(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(infoCategoryMapper, never()).countTravelInfoByCategoryId(99L);
        verify(infoCategoryMapper, never()).deleteById(99L);
    }

    private InfoCategoryForm form(String name, Integer displayOrder, Boolean isVisible) {
        InfoCategoryForm form = new InfoCategoryForm();
        form.setName(name);
        form.setDisplayOrder(displayOrder);
        form.setIsVisible(isVisible);
        return form;
    }

    private InfoCategory category(Long id, String name, Integer displayOrder, Boolean isVisible) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setIsVisible(isVisible);
        return category;
    }
}
