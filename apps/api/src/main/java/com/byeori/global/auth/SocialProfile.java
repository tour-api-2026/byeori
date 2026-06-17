package com.byeori.global.auth;

/** 소셜 제공자 검증 결과 공용 프로필. provider="KAKAO"|"GOOGLE". */
public record SocialProfile(String provider, String providerUserId, String email,
                            String nickname, String imageUrl) {}
