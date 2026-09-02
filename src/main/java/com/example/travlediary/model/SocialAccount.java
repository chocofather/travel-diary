package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class SocialAccount {

    private Long id;
    private Long userId;
    private SocialProvider provider;
    private String providerUserId;
    private String providerEmail;
    private Boolean providerEmailVerified;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
