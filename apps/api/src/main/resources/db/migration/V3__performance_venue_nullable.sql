-- KOPIS 공연은 우리 venue id와 매칭되지 않을 수 있어 venue_id를 nullable로 변경
ALTER TABLE performances ALTER COLUMN venue_id DROP NOT NULL;
