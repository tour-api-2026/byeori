package com.byeori.domain.feed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 점수 가중치. 빌드 없이 돌려 보고 고칠 수 있어야 해서 설정으로 뺀다.
 *
 * 네 항을 곱하지 않고 **더한다.** 곱하면 한 항이 0일 때 전체가 0이 되어, 좌표가 없는
 * 행사나 기록이 없는 사용자가 통째로 사라진다.
 */
@Component
@ConfigurationProperties(prefix = "byeori.feed")
public class FeedProperties {

    private double taste = 0.40;
    private double distance = 0.25;
    private double imminence = 0.20;
    private double popularity = 0.15;

    /** 점수를 매기기 전에 추릴 후보 수. 늘리면 더 고르지만 느려진다. 행사 쪽에 쓴다. */
    private int candidatePerKind = 200;

    /**
     * 장소 후보를 분류마다 몇 칸씩 뽑을지. 고른 분류와 나머지를 다르게 준다.
     *
     * 한 쿼리로 합쳐 뽑으면 낮은 id 를 가진 분류가 칸을 다 가져간다 — 음식·공예를 고른
     * 사람에게 맛집 168 · 카페 32 가 나오고 공예·전통시장은 0건이었다. 그래서 분류별로
     * 창을 나눈다. 나머지 분류에도 몫을 남기는 이유는 발견이다.
     *
     * 기본값으로 고른 분류 4개면 4×50 + 나머지 4×12 = 237 건이 후보가 된다.
     */
    private int candidateLikedCategory = 50;
    private int candidateOtherCategory = 12;

    /** 같은 분류가 연속으로 이만큼 나오면 다음 것을 뒤로 민다. */
    private int sameCategoryRun = 2;

    /** 같은 종류(장소/행사)가 연속으로 이만큼 나오면 뒤로 민다. */
    private int sameTypeRun = 3;

    public double getTaste() { return taste; }
    public void setTaste(double v) { this.taste = v; }
    public double getDistance() { return distance; }
    public void setDistance(double v) { this.distance = v; }
    public double getImminence() { return imminence; }
    public void setImminence(double v) { this.imminence = v; }
    public double getPopularity() { return popularity; }
    public void setPopularity(double v) { this.popularity = v; }
    public int getCandidatePerKind() { return candidatePerKind; }
    public void setCandidatePerKind(int v) { this.candidatePerKind = v; }
    public int getCandidateLikedCategory() { return candidateLikedCategory; }
    public void setCandidateLikedCategory(int v) { this.candidateLikedCategory = v; }
    public int getCandidateOtherCategory() { return candidateOtherCategory; }
    public void setCandidateOtherCategory(int v) { this.candidateOtherCategory = v; }
    public int getSameCategoryRun() { return sameCategoryRun; }
    public void setSameCategoryRun(int v) { this.sameCategoryRun = v; }
    public int getSameTypeRun() { return sameTypeRun; }
    public void setSameTypeRun(int v) { this.sameTypeRun = v; }
}
