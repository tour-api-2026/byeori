package com.byeori.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기존 users 테이블 매핑 + 소셜 식별자(provider_user_id)·프로필 이미지(V5). */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider")
    private String authProvider;

    private String role;

    @Column(name = "phone_verified")
    private boolean phoneVerified;

    @Column(name = "email_verified")
    private boolean emailVerified;

    private String language;
    private String status;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 소셜 신규 가입. authProvider는 "KAKAO"|"GOOGLE" 대문자. */
    public static User social(String authProvider, String providerUserId,
                              String name, String email, String profileImageUrl) {
        User u = new User();
        u.authProvider = authProvider;
        u.providerUserId = providerUserId;
        u.name = (name != null && !name.isBlank()) ? name : "사용자";
        u.email = email;
        u.profileImageUrl = profileImageUrl;
        u.role = "USER";
        u.status = "ACTIVE";
        u.language = "ko";
        u.phoneVerified = false;
        u.emailVerified = email != null && !email.isBlank();
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    /** 재로그인 시 변경된 프로필 동기화. */
    public void updateProfile(String name, String email, String profileImageUrl) {
        if (name != null && !name.isBlank()) this.name = name;
        if (email != null && !email.isBlank()) this.email = email;
        if (profileImageUrl != null && !profileImageUrl.isBlank()) this.profileImageUrl = profileImageUrl;
        this.updatedAt = LocalDateTime.now();
    }
}
