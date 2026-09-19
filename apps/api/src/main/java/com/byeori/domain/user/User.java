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

    /** 관리자 계정(아이디/비밀번호 로그인). role=ADMIN. */
    public static User admin(String providerUserId, String name) {
        User u = new User();
        u.authProvider = "ADMIN";
        u.providerUserId = providerUserId;
        u.name = (name != null && !name.isBlank()) ? name : "관리자";
        u.role = "ADMIN";
        u.status = "ACTIVE";
        u.language = "ko";
        u.phoneVerified = false;
        u.emailVerified = false;
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    /**
     * 스토어 심사용 계정(아이디/비밀번호 로그인). role=USER —
     * 심사자가 로그인 전용 기능(즐겨찾기·리뷰·마이페이지)만 확인하면 되므로 ADMIN 권한을 주지 않는다.
     */
    public static User review(String providerUserId, String name) {
        User u = new User();
        u.authProvider = "REVIEW";
        u.providerUserId = providerUserId;
        u.name = (name != null && !name.isBlank()) ? name : "심사용 계정";
        u.role = "USER";
        u.status = "ACTIVE";
        u.language = "ko";
        u.phoneVerified = false;
        u.emailVerified = false;
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    /**
     * 재로그인 시 제공자 프로필 반영.
     *
     * 닉네임·사진은 가입할 때만 제공자 값을 쓰고 이후에는 건드리지 않는다. 벼리에서 고치거나
     * 지운 값을 로그인할 때마다 카카오·구글 값으로 되돌리면 안 되기 때문이다. 이메일은 로그인
     * 계정에 묶인 값이라 사용자가 고칠 수 없고, 제공자 쪽 변경을 그대로 따른다.
     */
    public void syncFromProvider(String email) {
        if (email != null && !email.isBlank()) this.email = email;
        this.updatedAt = LocalDateTime.now();
    }

    /** 마이페이지에서 고친 닉네임·프로필 사진. 사진이 null 이면 지운다. */
    public void editProfile(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = LocalDateTime.now();
    }
}
