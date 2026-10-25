import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { usePathname } from 'expo-router';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { LOCALES, LOCALE_NAMES } from '@/lib/i18n';
import { useLocaleStore } from '@/lib/store/localeStore';
import { colors, fonts, radius } from '@/lib/theme';

/**
 * 웹에서 오른쪽 위에 두는 언어 전환 버튼.
 *
 * 앱은 첫 실행에 언어를 고르는 화면을 띄우지만, 웹은 그러지 않는다. 주소를 치고 들어온
 * 사람에게 아무것도 안 보여 준 채 선택을 먼저 요구하면 그게 무슨 사이트인지 모르는 상태로
 * 막히기 때문이다. 웹은 브라우저 언어로 바로 열고, 바꾸고 싶은 사람이 여기서 바꾼다
 * (_layout 의 chosen 게이트 참고).
 *
 * **탭 화면에서만 보인다.** 상세 화면은 오른쪽 위에 이미 하트·신고 아이콘이 있어서
 * 겹친다. 언어는 한 번 고르면 유지되므로 들어가는 길목인 탭에만 있으면 충분하다.
 */
export function WebLanguageSwitcher() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);

  if (Platform.OS !== 'web') return null;
  if (!TAB_PATHS.has(pathname)) return null;

  const pick = async (l: typeof locale) => {
    setOpen(false);
    if (l === locale) return;
    await setLocale(l);
    // 받아 둔 응답은 이전 언어다. 비워야 장소·행사 이름이 새 언어로 다시 온다
    // (my/language 와 같은 처리 — 한쪽만 고치면 설정에서 바꿀 때와 결과가 달라진다).
    await qc.invalidateQueries();
  };

  return (
    <View style={styles.wrap} pointerEvents="box-none">
      <Pressable style={styles.button} onPress={() => setOpen((v) => !v)}>
        <Ionicons name="globe-outline" size={15} color={colors.text} />
        <Text style={styles.label}>{LOCALE_NAMES[locale]}</Text>
        <Ionicons name={open ? 'chevron-up' : 'chevron-down'} size={13} color={colors.textFaint} />
      </Pressable>

      {open && (
        <View style={styles.menu}>
          {LOCALES.map((l) => (
            <Pressable key={l} style={styles.row} onPress={() => pick(l)}>
              {/* 그 언어의 자기 이름으로 적는다 — 지금 언어를 못 읽는 사람이 쓰는 버튼이다. */}
              <Text style={[styles.name, l === locale && styles.nameOn]}>{LOCALE_NAMES[l]}</Text>
              {l === locale && <Ionicons name="checkmark" size={15} color={colors.primary} />}
            </Pressable>
          ))}
        </View>
      )}
    </View>
  );
}

/** 탭 화면의 주소. (tabs) 는 그룹이라 주소에 나타나지 않는다. */
const TAB_PATHS = new Set(['/', '/explore', '/map', '/routes', '/profile']);

const styles = StyleSheet.create({
  // 화면 위에 띄우되 아래 내용의 클릭을 막지 않는다(pointerEvents="box-none").
  wrap: { position: 'absolute', top: 8, right: 12, zIndex: 50, alignItems: 'flex-end' },
  button: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: colors.bgCard, borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border,
    paddingHorizontal: 10, paddingVertical: 7,
  },
  label: { fontFamily: fonts.medium, fontSize: 13, color: colors.text },
  menu: {
    marginTop: 6, minWidth: 150,
    backgroundColor: colors.bgCard, borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border, overflow: 'hidden',
  },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 12, paddingVertical: 10,
  },
  name: { fontFamily: fonts.medium, fontSize: 14, color: colors.text },
  nameOn: { color: colors.primary },
});
