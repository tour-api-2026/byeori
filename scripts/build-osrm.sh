#!/usr/bin/env bash
# 도보 경로용 OSRM 데이터를 만든다. OSM 은 매일 갱신되므로 분기에 한 번쯤 돌린다.
#
# ⚠️ 돌리는 동안 Gradle·웹 빌드·로컬 API 를 띄우지 말 것.
#    extract 가 5.5GB 를 쓴다. 이 기기는 메모리가 바닥나면 커널이 아무 프로세스나
#    죽이고(전에 java 가 당했다), 그러면 postgres 나 운영 API 가 간다.
#    --memory 한도를 거는 이유가 그것이다 — 터져도 이 컨테이너만 죽는다.
set -euo pipefail
DIR="${1:-/home/hidi/osrm-data}"
IMG=ghcr.io/project-osrm/osrm-backend
PBF=south-korea-latest.osm.pbf
mkdir -p "$DIR"

echo "① 내려받기 (약 276MB)"
curl -sL -o "$DIR/$PBF" https://download.geofabrik.de/asia/south-korea-latest.osm.pbf

run() {                       # 한 단계를 한도 안에서 돌린다
  echo "② $1"
  docker run --rm --memory=5500m --memory-swap=8g -v "$DIR:/data" "$IMG" "$@"
}
# extract 는 실측 5,474MB 를 쓴다. 4GB·5GB 한도에서는 OOM 으로 죽었고,
# 스왑을 열어 주어야 통과한다.
run osrm-extract -p /opt/foot.lua "/data/$PBF"
run osrm-partition "/data/south-korea-latest.osrm"
run osrm-customize "/data/south-korea-latest.osrm"

echo "완료: $(du -sh "$DIR" | cut -f1)"
echo "컨테이너 재기동: cd infra && docker compose up -d --force-recreate osrm"
