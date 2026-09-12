"""벼리_기능설명서.pptx 를 그 자리에서 갱신한다.

이미지(화면 캡처 5장·흐름도 5장)가 들어간 산출물이라 처음부터 다시 만들면
잃는다. fill-form.py 는 이미지를 넣지 않으므로 재생성하면 안 된다.

    python3 docs/submission/update-deck.py <입력.pptx> <출력.pptx>
"""
import copy, sys
from pptx import Presentation
from pptx.util import Pt
from pptx.dml.color import RGBColor
from pptx.text.text import _Paragraph

BODY = RGBColor(0x1A, 0x1A, 0x1F)


def style(run, size):
    """양식의 빨간 가이드 서식이 상속되므로 본문 서식을 명시한다."""
    run.font.color.rgb = BODY
    run.font.bold = False
    if size:
        run.font.size = Pt(size)


def set_cell(cell, text, size=None):
    """셀 텍스트 교체. run.text 에만 쓴다 — text_frame.text 대입은 서식이 날아간다."""
    tf = cell.text_frame
    lines = text.split('\n')
    p0 = tf.paragraphs[0]
    if not p0.runs:
        p0.add_run()
    p0.runs[0].text = lines[0]
    style(p0.runs[0], size)
    for extra in p0.runs[1:]:
        extra._r.getparent().remove(extra._r)
    for p in tf.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    for line in lines[1:]:
        newp = copy.deepcopy(p0._p)
        p0._p.getparent().append(newp)
        para = _Paragraph(newp, tf)
        para.runs[0].text = line
        style(para.runs[0], size)


def table_of(slide, rows, cols):
    for sh in slide.shapes:
        if sh.has_table and len(sh.table.rows) == rows and len(sh.table.columns) == cols:
            return sh.table
    sys.exit(f'표를 찾지 못함 {rows}x{cols}')


src, out = sys.argv[1], sys.argv[2]
prs = Presentation(src)

# ── 3쪽 핵심기능 ────────────────────────────────────────
set_cell(table_of(prs.slides[2], 1, 2).cell(0, 1),
         '- 전통 테마 행사 큐레이션 기능\n'
         '   공연·축제 데이터에서 전통을 주제로 한 행사만 자동 분류해 1,359건 제공\n'
         '- 위치 기반 지도 탐색 기능\n'
         '   지도가 멈춘 좌표·반경으로 공사 OpenAPI를 실시간 조회해 그 영역의 장소만 표시\n'
         '- 명칭 검색 및 지역별 탐색 기능\n'
         '   검색어와 지역 좌표로 공사 OpenAPI를 실시간 조회. 숙박·쇼핑·레포츠까지 전 유형 대상\n'
         '- 장소 상세 정보 제공 기능\n'
         '   소개글·이용시간·휴무일·문의처를 실시간 조회하고, 진행 중인 행사를 함께 제공\n'
         '- 여행 일정 생성 및 경로 안내 기능\n'
         '   방문지를 담아 하루 일정을 구성하고 실제 이동 경로와 소요 시간 확인\n'
         '- 리뷰 및 이용자 보호 기능\n'
         '   방문 후기 작성, 부적절한 콘텐츠 신고, 특정 이용자 차단')

# ── 10쪽 공사 OpenAPI 목록 (칸이 5개라 성격이 같은 것끼리 묶는다) ──
APIS = [
    ('한국관광공사 국문 관광정보 서비스 (locationBasedList2)',
     '지도 주변 탐색과 시·도 지역 탐색에 실시간 호출. 보고 있는 좌표와 반경의 장소만 조회하며, 지도 이동 1회당 1회 호출'),
    ('한국관광공사 국문 관광정보 서비스 (searchKeyword2)',
     '검색 화면에 실시간 호출. 이용자가 입력한 검색어로 조회하며, 입력이 멈춘 뒤 0.4초 후 1회 호출'),
    ('한국관광공사 국문 관광정보 서비스 (detailCommon2 / detailIntro2)',
     '장소 상세를 열 때마다 실시간 호출하여 소개글·이용시간·휴무일·문의처·주차를 조회. '
     '저장 목록에 없는 장소도 콘텐츠 ID로 동일하게 조회. 1건당 2회 호출'),
    ('한국관광공사 국문 관광정보 서비스 (areaBasedSyncList2)',
     '공사가 로컬 저장용으로 제공하는 동기화 API. 1일 1회 직전 수집 이후 변경분만 수신(1회 약 3건 호출)하며, '
     'showflag로 공사가 내린 콘텐츠를 함께 비노출 처리'),
    ('한국관광공사 국문 관광정보 서비스 (searchFestival2 / areaBasedList2 / ldongCode2)',
     '행사·축제 정보 수집(417건)과 초기 장소 적재(29,536건). 전통 테마 행사 분류와 지역 매핑에 활용'),
]
t = table_of(prs.slides[9], 10, 3)
for i, (name, desc) in enumerate(APIS):
    set_cell(t.cell(i * 2, 2), name, size=12)
    set_cell(t.cell(i * 2 + 1, 2), desc, size=11)

# ── 12쪽 차별성 ─────────────────────────────────────────
set_cell(table_of(prs.slides[11], 2, 2).cell(0, 1),
         '1. 장소 정보와 공연 정보의 결합\n'
         '   대부분의 관광 서비스는 장소 또는 공연 한쪽만 다룹니다. 벼리는 한국관광공사 OpenAPI의 장소\n'
         '   데이터와 공연 데이터를 연결해, 장소 상세에서 "여기서 지금 무엇이 열리는지"를 함께 보여줍니다.\n'
         '2. 전통 행사 자동 분류\n'
         '   공공데이터는 전통 행사를 따로 구분해 주지 않습니다. 장르 코드만으로는 놓치는 행사가 많아\n'
         '   제목·주최기관을 함께 검사하는 규칙으로 1,359건을 선별했습니다.\n'
         '3. 필요한 만큼만 부르는 실시간 조회\n'
         '   지도·검색·지역탐색·상세를 모두 공사 OpenAPI 실시간 호출로 구현했습니다. 전국 데이터를 미리\n'
         '   받아두지 않고 보고 있는 영역만 부르며, 좌표를 반올림해 같은 화면을 다시 부르지 않습니다.\n'
         '4. 공사 데이터와 어긋나지 않는 운영\n'
         '   공사가 제공하는 동기화 API로 변경분만 받고(1일 3회 호출), 공사가 내린 콘텐츠는 showflag로\n'
         '   확인해 즉시 비노출 처리합니다. 사라진 장소가 서비스에 남지 않습니다.\n'
         '5. 한복 착용 혜택 정보\n'
         '   한복 착용 시 혜택이 있는 장소를 별도로 표시해 전통문화 체험이 하나의 동선으로 이어지게 했습니다.\n'
         '6. 진입 장벽 없는 이용\n'
         '   장소·공연 조회와 지도 탐색은 로그인 없이 모두 이용할 수 있습니다.', size=10.5)

prs.save(out)
print('저장:', out)
