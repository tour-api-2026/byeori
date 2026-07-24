package com.byeori.global.external;

import com.byeori.global.external.dto.KopisItem;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** KOPIS(공연예술통합전산망) OpenAPI 클라이언트. 응답은 XML. */
@Component
@Slf4j
public class KopisClient {

    private static final String BASE = "http://www.kopis.or.kr/openApi/restful";
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SyncProperties props;
    private final RestClient http = RestClient.create();

    public KopisClient(SyncProperties props) {
        this.props = props;
    }

    /** 공연목록(pblprfr). */
    public List<KopisItem> performances(LocalDate from, LocalDate to, int page, int rows) {
        if (!props.kopisEnabled()) return List.of();
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + "/pblprfr")
                    .queryParam("service", props.getKopisKey())
                    .queryParam("stdate", from.format(YMD))
                    .queryParam("eddate", to.format(YMD))
                    .queryParam("cpage", page)
                    .queryParam("rows", rows)
                    .encode()
                    .build()
                    .toUriString();
            String xml = http.get().uri(url).retrieve().body(String.class);
            return parse(xml);
        } catch (Exception e) {
            log.warn("KOPIS 공연목록 실패 page={}: {}", page, e.getMessage());
            return List.of();
        }
    }

    /** 공연상세(pblprfr/{mt20id}) → 공연시설ID(mt10id). 좌표 보강용. */
    public String facilityId(String mt20id) {
        if (!props.kopisEnabled() || mt20id == null) return null;
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + "/pblprfr/" + mt20id)
                    .queryParam("service", props.getKopisKey())
                    .encode().build().toUriString();
            String xml = http.get().uri(url).retrieve().body(String.class);
            return firstTag(xml, "mt10id");
        } catch (Exception e) {
            log.debug("KOPIS 공연상세 실패 {}: {}", mt20id, e.getMessage());
            return null;
        }
    }

    /** 공연시설상세(prfplc/{mt10id}) → [위도(la), 경도(lo)]. 좌표 없으면 null. */
    public java.math.BigDecimal[] facilityCoords(String mt10id) {
        if (!props.kopisEnabled() || mt10id == null) return null;
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + "/prfplc/" + mt10id)
                    .queryParam("service", props.getKopisKey())
                    .encode().build().toUriString();
            String xml = http.get().uri(url).retrieve().body(String.class);
            String la = firstTag(xml, "la");
            String lo = firstTag(xml, "lo");
            if (la == null || lo == null) return null;
            return new java.math.BigDecimal[]{new java.math.BigDecimal(la), new java.math.BigDecimal(lo)};
        } catch (Exception e) {
            log.debug("KOPIS 공연시설상세 실패 {}: {}", mt10id, e.getMessage());
            return null;
        }
    }

    /** XML에서 첫 번째 태그 텍스트 추출(단건 응답용). */
    private String firstTag(String xml, String name) throws Exception {
        if (xml == null || xml.isBlank()) return null;
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList n = doc.getElementsByTagName(name);
        if (n.getLength() == 0 || n.item(0).getTextContent() == null) return null;
        String v = n.item(0).getTextContent().trim();
        return v.isBlank() ? null : v;
    }

    private List<KopisItem> parse(String xml) throws Exception {
        List<KopisItem> out = new ArrayList<>();
        if (xml == null || xml.isBlank()) return out;
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList list = doc.getElementsByTagName("db");
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) node;
            out.add(new KopisItem(
                    tag(e, "mt20id"), tag(e, "prfnm"), tag(e, "poster"),
                    tag(e, "prfpdfrom"), tag(e, "prfpdto"), tag(e, "genrenm"), tag(e, "prfstate")));
        }
        return out;
    }

    private static String tag(Element e, String name) {
        NodeList n = e.getElementsByTagName(name);
        if (n.getLength() == 0 || n.item(0).getTextContent() == null) return null;
        String v = n.item(0).getTextContent().trim();
        return v.isBlank() ? null : v;
    }
}
