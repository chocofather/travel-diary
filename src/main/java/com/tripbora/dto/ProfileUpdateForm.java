package com.tripbora.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
public class ProfileUpdateForm {
    private String nickname;
    private MultipartFile profileImageFile;
}
