package com.example.travlediary.repository.user;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SocialAccountMapper {

    SocialAccount findByProviderAndProviderUserId(
            @Param("provider") SocialProvider provider,
            @Param("providerUserId") String providerUserId);

    SocialAccount findByUserIdAndProvider(@Param("userId") Long userId,
                                           @Param("provider") SocialProvider provider);

    List<SocialAccount> findAllByUserId(@Param("userId") Long userId);

    int insert(SocialAccount socialAccount);

    int deleteAllByUserId(@Param("userId") Long userId);
}
