#!/usr/bin/env bash
# 도보 경로용 OSRM 데이터를 만든다. OSM 은 매일 갱신되므로 분기에 한 번쯤 돌린다.
#
# ⚠️ 돌리는 동안 Gradle·웹 빌드·로컬 API 를 띄우지 말 것.
#    extract 는 메모리 한도 5500m 에 스왑 2.5GB(memory-swap 8g)까지 쓴다.
#    이 기기는 메모리가 바닥나면 커널이 아무 프로세스나 죽이고(전에 java 가 당했다),
#    그러면 postgres 나 운영 API 가 간다. 한도를 거는 이유는 터져도 이 컨테이너만
#    죽게 하려는 것이다.
#
# 재빌드 중에는 osrm 컨테이너를 멈춘다. 서버가 .osrm 파일을 mmap 한 채로 두고
# 그 파일을 덮어쓰면 SIGBUS 로 죽거나 섞인 데이터를 서빙할 수 있기 때문이다.
# 멈춰 있는 동안 도보 경로는 자동차 경로로 대체된다. 이는 예상된 동작이다.
set -euo pipefail
STOPPED=0
on_exit() {                   # 실패로 끝났을 때만 복구 방법을 알린다
  local rc=$?
  [ "$rc" -eq 0 ] && return
  echo "" >&2
  echo "재빌드가 실패했다(종료 코드 $rc)." >&2
  if [ "$STOPPED" = 1 ]; then
    echo "osrm 이 정지된 상태다. 도보 경로는 지금 자동차 경로로 대체되고 있다." >&2
    echo "기존 데이터로 되돌리려면: docker compose -f $COMPOSE up -d osrm" >&2
    echo "(partition/customize 단계에서 실패했다면 산출물이 섞였을 수 있으니 재빌드부터 한다.)" >&2
  fi
}
trap on_exit EXIT
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="$ROOT/infra/docker-compose.yml"
# 산출물 위치. infra/docker-compose.yml 의 osrm 볼륨 마운트와 반드시 같아야 한다.
# 다른 곳에 만들면 재기동된 서버는 예전 데이터를 계속 서빙한다.
DIR=/home/hidi/osrm-data
IMG=ghcr.io/project-osrm/osrm-backend:v26.10.0-amd64-debian   # 기존 데이터를 만든 버전. 올리면 포맷이 바뀔 수 있다.
PBF=south-korea-latest.osm.pbf
mkdir -p "$DIR"

# ⓪ 쓸 수 없는 산출물이 있으면 **아무것도 멈추기 전에** 멈춘다.
#
# 컨테이너를 호스트 사용자로 돌리기 전에 만든 데이터는 root 소유 644 로 남아 있다.
# 그 상태로 재빌드를 시작하면 osrm-extract 가 기존 파일을 비우지 못해 실패하는데,
# 그때는 이미 서빙 중인 컨테이너를 멈춘 뒤다 — 도보 경로가 통째로 내려앉는다.
# 지우지도, 몰래 chown 하지도 않는다. 운영자가 보고 결정할 일이다.
BLOCKED="$(find "$DIR" -maxdepth 1 -type f ! -writable -printf '%f\n' 2>/dev/null | sort)"
if [ -n "$BLOCKED" ]; then
  echo "중단: $DIR 에 이 사용자가 덮어쓸 수 없는 파일이 있다." >&2
  echo "$BLOCKED" | sed 's/^/  - /' >&2
  echo "" >&2
  echo "재빌드를 시작하면 extract 가 이 파일들을 비우지 못해 실패하는데, 그때는 이미" >&2
  echo "osrm 컨테이너를 멈춘 뒤다. 그래서 멈추기 전에 끊는다(지금 서빙은 그대로다)." >&2
  echo "소유권을 넘긴 뒤 다시 실행한다:" >&2
  echo "  sudo chown -R \"\$(id -u):\$(id -g)\" $DIR" >&2
  exit 1
fi

echo "① 내려받기 (약 276MB)"
# -f: HTTP 오류 응답 본문을 데이터로 저장하지 않는다. 임시 파일에 받고 성공해야 교체한다.
curl -fsSL -o "$DIR/$PBF.tmp" https://download.geofabrik.de/asia/south-korea-latest.osm.pbf
mv "$DIR/$PBF.tmp" "$DIR/$PBF"

echo "② osrm 컨테이너 정지"
docker compose -f "$COMPOSE" stop osrm
STOPPED=1

run() {                       # 한 단계를 한도 안에서 돌린다
  echo "③ $1"
  # 컨테이너를 호스트 사용자로 돌려 산출물이 root 소유가 되지 않게 한다.
  docker run --rm --user "$(id -u):$(id -g)" --memory=5500m --memory-swap=8g -v "$DIR:/data" "$IMG" "$@"
}
# extract 는 실측 5,474MB 를 쓴다. 4GB·5GB 한도에서는 OOM 으로 죽었고,
# 스왑을 열어 주어야 통과한다.
run osrm-extract -p /opt/foot.lua "/data/$PBF"
run osrm-partition "/data/south-korea-latest.osrm"
run osrm-customize "/data/south-korea-latest.osrm"

echo "④ osrm 컨테이너 재기동"
docker compose -f "$COMPOSE" up -d --force-recreate osrm

echo "완료: $(du -sh "$DIR" | cut -f1)"
