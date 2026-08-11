package com.example.travlediary.repository.faq;

import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaqMapper {

    List<FaqListItemDto> findAdminList();

    List<FaqListItemDto> findPublicList();

    List<FaqCategory> findCategories();

    FaqCategory findCategoryById(@Param("id") Long id);

    Faq findById(@Param("id") Long id);

    Faq findByIdForUpdate(@Param("id") Long id);

    int insertFaq(Faq faq);

    int updateFaq(Faq faq);

    int deleteFaq(@Param("id") Long id);
}
